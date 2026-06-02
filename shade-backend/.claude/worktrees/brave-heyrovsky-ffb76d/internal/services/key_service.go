package services

import (
	"context"
	"core-backend/internal/dto"
	"core-backend/internal/repositories"
)

type KeyService interface {
	GetPublicKey(ctx context.Context, targetID string) (*dto.GetPublicKeyResponse, error)
}

type keyService struct{ repo repositories.KeyRepository }

func NewKeyService(r repositories.KeyRepository) KeyService {
	return &keyService{repo: r}
}

func (s *keyService) GetPublicKey(ctx context.Context, targetID string) (*dto.GetPublicKeyResponse, error) {
	uk, err := s.repo.GetByUserID(ctx, targetID)
	if err != nil {
		return nil, err
	}
	return &dto.GetPublicKeyResponse{
		CoreGuardID: targetID,
		PublicKey:   uk.IdentityPublicKey,
	}, nil
}
