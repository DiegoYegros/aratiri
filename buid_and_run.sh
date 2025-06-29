#!/bin/bash
echo "Running mvn clean install"
mvn clean install || { echo "Maven clean install failed"; exit 1; }

echo "Building and Running Docker image"
docker-compose up --build