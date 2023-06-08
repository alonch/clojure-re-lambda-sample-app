build-ClojureAwsLambdaJavaFunction:
	lein uberjar
	mkdir -p $(ARTIFACTS_DIR)/lib
	mv target/uberjar/bootstrap.jar $(ARTIFACTS_DIR)/lib