build-ClojureAwsLambdaJavaFunction:
	yarn shadow-cljs release lib
	mv dist/script.js $(ARTIFACTS_DIR)/