# Docker container used to build the Clojure app
FROM clojure:openjdk-11-slim-buster as builder

WORKDIR /usr/src/app

COPY project.clj /usr/src/app/project.clj

# Cache deps so they aren't fetched every time a .clj file changes
RUN lein deps

COPY src/ /usr/src/app/src

RUN lein uberjar


# Build the docker container we will use in the lambda
FROM eclipse-temurin:11-focal

RUN mkdir /opt/app

COPY --from=builder /usr/src/app/target/bootstrap.jar /opt/app/app.jar

ENTRYPOINT [ "java", "-cp", "/opt/app/app.jar", "com.amazonaws.services.lambda.runtime.api.client.AWSLambda" ]

CMD ["clojure_aws_lambda_example.core::handleRequest"]
