package handlers

import (
	"core-backend/internal/services"

	"github.com/gofiber/fiber/v2"
)

type KeyHandler struct {
	keyService services.KeyService
}

func NewKeyHandler(keyService services.KeyService) *KeyHandler {
	return &KeyHandler{keyService: keyService}
}

func (h *KeyHandler) GetPublicKey(c *fiber.Ctx) error {
	targetID := c.Params("id")

	res, err := h.keyService.GetPublicKey(c.Context(), targetID)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
			"error": "Açık anahtar bulunamadı",
		})
	}

	return c.Status(fiber.StatusOK).JSON(res)
}
