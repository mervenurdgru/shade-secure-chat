package main

import (
	"fmt"
	"os"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/joho/godotenv"
)

func main() {
	godotenv.Load()
	secret := os.Getenv("JWT_SECRET")
	if secret == "" {
		secret = "fallback-dev-secret-key"
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"user_id":       "c602aa69-4aaa-4f3f-adc1-7b28fb93f7b9",
		"core_guard_id": "CG-5C9B-19A1",
		"device_id":     "e54f412a-12b4-4c85-93b4-d484d2377407",
		"exp":           time.Now().Add(time.Hour * 24).Unix(),
	})

	signed, err := token.SignedString([]byte(secret))
	if err != nil {
		fmt.Println("HATA:", err)
		return
	}
	fmt.Println(signed)
}
