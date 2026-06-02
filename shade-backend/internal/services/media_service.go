package services

import (
	"bytes"
	"context"
	"core-backend/internal/dto"
	"core-backend/internal/models"
	"core-backend/internal/repositories"
	"core-backend/pkg/logger"
	"errors"
	"io"
	"net/http"

	"github.com/google/uuid"
	"go.uber.org/zap"
)

var allowedMIMETypes = map[string]bool{
	"image/jpeg": true,
	"image/png":  true,
	"image/webp": true,
	"image/gif":  true,
}

func validateFileType(fileData io.Reader) (io.Reader, error) {
	header := make([]byte, 512)
	n, err := fileData.Read(header)
	if err != nil && err != io.EOF {
		return nil, errors.New("failed to read file header")
	}
	header = header[:n]

	mimeType := http.DetectContentType(header)
	if !allowedMIMETypes[mimeType] {
		return nil, errors.New("invalid file type: only JPEG, PNG, WebP and GIF images are allowed")
	}

	return io.MultiReader(bytes.NewReader(header), fileData), nil
}

type MediaService interface {
	Upload(ctx context.Context, uploaderID uuid.UUID, fileData io.Reader, fileSize int64) (*dto.UploadImageResponse, error)
	Download(ctx context.Context, imageID uuid.UUID, requestingUserID uuid.UUID) (io.ReadCloser, error)
}

type mediaService struct {
	repo        repositories.MediaRepository
	maxFileSize int64
}

func NewMediaService(repo repositories.MediaRepository, maxFileSize int64) MediaService {
	return &mediaService{repo: repo, maxFileSize: maxFileSize}
}

func (s *mediaService) Upload(ctx context.Context, uploaderID uuid.UUID, fileData io.Reader, fileSize int64) (*dto.UploadImageResponse, error) {
	if fileSize > s.maxFileSize {
		return nil, errors.New("file size exceeds maximum allowed size")
	}

	validatedReader, err := validateFileType(fileData)
	if err != nil {
		logger.Log.Warn("invalid file type upload attempt",
			zap.String("uploader_id", uploaderID.String()),
			zap.Error(err),
		)
		return nil, err
	}

	imageID := uuid.New()

	if err := s.repo.UploadToStorage(ctx, imageID.String(), validatedReader, fileSize); err != nil {
		logger.Log.Error("failed to upload image to storage", zap.Error(err))
		return nil, err
	}

	metadata := &models.ImageMetaData{
		ImageID:    imageID,
		UploaderID: uploaderID,
		Size:       fileSize,
	}

	if err := s.repo.SaveMetadata(ctx, metadata); err != nil {
		logger.Log.Error("failed to save image metadata", zap.Error(err))
		return nil, err
	}

	logger.Log.Info("image uploaded successfully",
		zap.String("image_id", imageID.String()),
		zap.String("uploader_id", uploaderID.String()),
	)

	return &dto.UploadImageResponse{
		ImageID: imageID.String(),
	}, nil
}

func (s *mediaService) Download(ctx context.Context, imageID uuid.UUID, requestingUserID uuid.UUID) (io.ReadCloser, error) {
	metadata, err := s.repo.GetMetadata(ctx, imageID)
	if err != nil {
		return nil, err
	}

	if metadata.UploaderID != requestingUserID {
		logger.Log.Warn("IDOR attempt blocked",
			zap.String("image_id", imageID.String()),
			zap.String("owner_id", metadata.UploaderID.String()),
			zap.String("requester_id", requestingUserID.String()),
		)
		return nil, errors.New("forbidden")
	}

	body, err := s.repo.DownloadFromStorage(ctx, imageID.String())
	if err != nil {
		return nil, err
	}

	return body, nil
}
