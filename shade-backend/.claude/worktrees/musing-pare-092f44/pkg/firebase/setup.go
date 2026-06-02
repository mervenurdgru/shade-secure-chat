package firebase

import (
	"context"
	"core-backend/pkg/logger"

	firebase "firebase.google.com/go/v4"
	"google.golang.org/api/option"
)

func InitFirebase() *firebase.App {
	opt := option.WithCredentialsFile("firebase-adminsdk.json")

	app, err := firebase.NewApp(context.Background(), nil, opt)
	if err != nil {
		logger.Log.Fatal("Firebase can not start! Check the JSON file")
	}

	logger.Log.Info("Firebase Admin SDK is running")
	return app
}
