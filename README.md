# Reward Points Service

This application provides an API for batch processing reward points for users and retrieving these reward points.

### Prerequisites

The following will need to be installed:

* Java 17
* Docker
* Docker Compose
* Gradle
* Kotlin

### Running the Application
Start the MongoDB service using Docker Compose:

```bash
docker-compose up
```
Build the application using Gradle:

```bash
./gradlew clean build
```
Start the application:

```bash
./gradlew bootRun
```
The application will be on http://localhost:8080.

### Usage

Endpoints:

POST /rewards/batch: Processes a CSV file containing rewards data. The endpoint returns a process Id, which you can then use to subscribe to the websocket server for updates on its progress

GET /rewards/{userId}: Returns the total rewards for a specific user for a given month and year. The month and year are passed as query parameters in the format MMYYYY (ISO format).

GET /rewards/range/{userId}: Returns a list of rewards for a given range of months and a specific year for a user.

WS /updates: The initial subscribe endpoint

WS /batch/{processId}: Will subscribe to the current batch process topic

### Testing

The util directory contains a Kotlin script for generating large CSV files for testing purposes. Feel free to tweak the hard-coded values to generate any csv file size up to 1GB

You can run the script using Kotlin:

```bash
kotlinc -script generate_csv.kts
```
This will generate a CSV file named test.csv in same `util` directory (but you can also change this in the script if you desire)

The util directory also contains a super boilerplate html file that allows you to subscribe to the WS topic. This was provided because tools like 
Postman and Insomnia do not support Stomp Clients.