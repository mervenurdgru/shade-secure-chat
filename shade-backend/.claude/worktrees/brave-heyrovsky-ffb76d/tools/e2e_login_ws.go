package main

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

const baseHTTP = "http://127.0.0.1:8080/api/v1"
const baseWS = "ws://127.0.0.1:8080/api/v1/ws"

type RegisterRequest struct {
	PublicKey           string `json:"public_key"`
	EncryptedPrivateKey string `json:"encrypted_private_key"`
	Salt                string `json:"salt"`
	DeviceModel         string `json:"device_model"`
	FCMToken            string `json:"fcm_token"`
}
type RegisterResponse struct {
	CoreGuardID string `json:"core_guard_id"`
	Message     string `json:"message"`
}

type LoginInitRequest struct {
	CoreGuardID string `json:"core_guard_id"`
}
type LoginInitResponse struct {
	EncryptedPrivateKey string `json:"encrypted_private_key"`
	Salt                string `json:"salt"`
	Challenge           string `json:"challenge"`
}

type LoginVerifyRequest struct {
	CoreGuardID string `json:"core_guard_id"`
	Challenge   string `json:"challenge"`
	Signature   string `json:"signature"`
	DeviceModel string `json:"device_model"`
	FCMToken    string `json:"fcm_token"`
}
type LoginVerifyResponse struct {
	AccessToken string `json:"access_token"`
	Message     string `json:"message"`
}

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func postJSON[T any](path string, body any) (int, T, []byte) {
	var out T

	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", baseHTTP+path, bytes.NewReader(b))
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	must(err)
	defer resp.Body.Close()

	raw, _ := io.ReadAll(resp.Body)

	_ = json.Unmarshal(raw, &out)
	return resp.StatusCode, out, raw
}

func main() {
	fmt.Println("== E2E: register -> login/init -> login/verify -> ws 101 ==")

	pub, priv, err := ed25519.GenerateKey(nil)
	must(err)
	pubHex := hex.EncodeToString(pub)

	regStatus, regRes, regRaw := postJSON[RegisterResponse]("/auth/register", RegisterRequest{
		PublicKey:           pubHex,
		EncryptedPrivateKey: "DUMMY_ENC_PRIV",
		Salt:                "DUMMY_SALT",
		DeviceModel:         "e2e-test",
		FCMToken:            "e2e-test",
	})
	if regStatus != 201 && regStatus != 200 {
		fmt.Printf("register failed: status=%d body=%s\n", regStatus, string(regRaw))
		os.Exit(1)
	}
	fmt.Println("register ok:", regRes.CoreGuardID)

	initStatus, initRes, initRaw := postJSON[LoginInitResponse]("/auth/login/init", LoginInitRequest{
		CoreGuardID: regRes.CoreGuardID,
	})
	if initStatus != 200 {
		fmt.Printf("login/init failed: status=%d body=%s\n", initStatus, string(initRaw))
		os.Exit(1)
	}
	fmt.Println("login/init ok. challenge:", initRes.Challenge)

	chBytes, err := hex.DecodeString(strings.TrimSpace(initRes.Challenge))
	must(err)
	sig := ed25519.Sign(priv, chBytes)
	sigHex := hex.EncodeToString(sig)

	verifyStatus, verifyRes, verifyRaw := postJSON[LoginVerifyResponse]("/auth/login/verify", LoginVerifyRequest{
		CoreGuardID: regRes.CoreGuardID,
		Challenge:   initRes.Challenge,
		Signature:   sigHex,
		DeviceModel: "e2e-test",
		FCMToken:    "e2e-test",
	})
	if verifyStatus != 200 {
		fmt.Printf("login/verify failed: status=%d body=%s\n", verifyStatus, string(verifyRaw))
		os.Exit(1)
	}
	if verifyRes.AccessToken == "" {
		fmt.Printf("login/verify no token: body=%s\n", string(verifyRaw))
		os.Exit(1)
	}
	fmt.Println("login/verify ok. token (first 20 chars):", verifyRes.AccessToken[:20])

	u, _ := url.Parse(baseWS)
	d := websocket.Dialer{
		Proxy: http.ProxyFromEnvironment,
	}
	header := http.Header{}
	header.Set("Authorization", "Bearer "+verifyRes.AccessToken)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	conn, resp, err := d.DialContext(ctx, u.String(), header)
	if err != nil {
		if resp != nil {
			b, _ := io.ReadAll(resp.Body)
			_ = resp.Body.Close()
			fmt.Printf("ws dial failed: %v status=%d body=%s\n", err, resp.StatusCode, string(b))
		} else {
			fmt.Printf("ws dial failed: %v\n", err)
		}
		os.Exit(1)
	}
	defer conn.Close()

	fmt.Println("WS connected (expected 101 Switching Protocols)")
	fmt.Println("Handshake status:", resp.Status)

	_ = conn.WriteMessage(websocket.TextMessage, []byte("ping"))
	_, msg, _ := conn.ReadMessage()
	fmt.Println("ws read:", string(msg))
}
