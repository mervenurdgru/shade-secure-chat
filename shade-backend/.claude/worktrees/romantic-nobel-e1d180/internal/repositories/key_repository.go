package repositories

import (
	"context"
	"core-backend/internal/models"

	"gorm.io/gorm"
)

type KeyRepository interface {
	GetByUserID(ctx context.Context, userID string) (*models.UserKey, error)
}

type keyRepository struct{ db *gorm.DB }

func NewKeyRepository(db *gorm.DB) KeyRepository {
	return &keyRepository{db: db}
}

func (r *keyRepository) GetByUserID(ctx context.Context, userID string) (*models.UserKey, error) {
	var userKey models.UserKey
	if err := r.db.WithContext(ctx).
		Where("user_id = ?", userID).
		First(&userKey).Error; err != nil {
		return nil, err
	}
	return &userKey, nil
}
