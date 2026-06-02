package dto

type GetPublicKeyResponse struct {
	CoreGuardID string `json:"core_guard_id"`
	PublicKey   string `json:"public_key"`
}
