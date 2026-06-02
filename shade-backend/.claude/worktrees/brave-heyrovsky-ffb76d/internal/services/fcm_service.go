package services

import (
	"context"
	"core-backend/pkg/logger"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/messaging"
	"go.uber.org/zap"
)

type FCMService interface {
	SendWakeUpSignal(fcmToken string) error
}

type fcmService struct {
	app *firebase.App
}

func NewFCMService(app *firebase.App) FCMService {
	return &fcmService{app: app}
}

func (s *fcmService) SendWakeUpSignal(fcmToken string) error {
	client, err := s.app.Messaging(context.Background())
	if err != nil {
		logger.Log.Error("FCM Client can not be created", zap.Error(err))
	}

	message := &messaging.Message{
		Token: fcmToken,
		Data: map[string]string{
			"type":   "NEW_ENCRYPTED_MESSAGE",
			"action": "SYNC_REQUIRED",
		},
	}

	response, err := client.Send(context.Background(), message)
	if err != nil {
		logger.Log.Error("FCM notification can not send", zap.Error(err), zap.String("token", fcmToken))
		return err
	}

	logger.Log.Info("FCM WakeUp signal successfully sent", zap.String("response", response))
	return nil
}
