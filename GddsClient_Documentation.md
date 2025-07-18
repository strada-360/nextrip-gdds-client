# GddsClient Technical Documentation

## Overview

The `GddsClient` implementations in C#, Java, and Python enable interaction with the `GddsStreamService`, a gRPC-based server streaming GPS Data Distribution System (GDDS) records, a vehicle tracking system based on the NMEA industry standard. Clients support requesting a client ID, subscribing to the GDDS stream, receiving real-time vehicle data, and terminating subscriptions. Each client uses gRPC interceptors to attach an API key (`x-api-key`) for authentication, ensuring secure access. Transit agencies can use this service to share data with third-party developers, enabling applications like vehicle tracking, navigation, and trip prediction.

Source code is available at: [GddsStreaming Repository](https://github.com/your-organization/gdds-streaming) (replace with actual URL).

## Architecture

Clients interact with the `GddsStreamService` via three gRPC methods:
- `GenerateClientId`: Requests a unique client ID for access control.
- `Subscribe`: Subscribes to a stream of `GddsMessage` objects using the client ID and API key.
- `Terminate`: Ends the subscription and cleans up resources.

### Proto File

The clients use the following Protocol Buffers definition, shared with the server: [gdds_streaming.proto](https://github.com/your-organization/gdds-streaming/blob/main/GddsStreaming/Protos/gdds_streaming.proto) (replace with actual path).

```proto
syntax = "proto3";

package GddsStreaming;

message GddsMessage {
    string ip = 1;
    string veh = 2;
    int64 tm = 3;
    double lat = 4;
    double lon = 5;
    double hdg = 6;
    double spd = 7;
    int32 trnTyp = 8;
    string msg = 9;
    int64 svrtm = 10;
}

message SubscriptionRequest {
    string clientId = 1;
}

message TerminationRequest {
    string clientId = 1;
}

message GenerateClientIdRequest {
}

message GenerateClientIdResponse {
    string clientId = 1;
}

service GddsStreamService {
    rpc Subscribe(SubscriptionRequest) returns (stream GddsMessage);
    rpc Terminate(TerminationRequest) returns (TerminationResponse);
    rpc GenerateClientId(GenerateClientIdRequest) returns (GenerateClientIdResponse);
}

message TerminationResponse {
    bool success = 1;
}
```

## Client Implementations

### C# Client

- **Source**: [GddsClient.cs](https://github.com/your-organization/gdds-streaming/blob/main/GddsStreaming.Client/GddsClient.cs) (replace with actual path).
- **Key Components**:
  - `GddsClient`: Manages the gRPC channel, client ID, API key, and subscription lifecycle.
  - `ApiKeyClientInterceptor`: Attaches the `x-api-key` header to all requests.
  - `GetClientIdAsync`: Requests a client ID from the server.
  - `StartReceivingAsync`: Subscribes to the GDDS stream and processes messages asynchronously.
  - `TerminateAsync`: Sends a termination request and cancels the stream.
  - `VehicleType`: Enum for transportation modes (e.g., Car, Truck, Bus).
- **Dependencies**:
  - NuGet packages: `Grpc.Net.Client`, `Google.Protobuf`, `Grpc.Tools` (version 2.66.0 or later).
- **Setup**:
  ```bash
  dotnet restore
  dotnet build
  ```
  Include the proto file for gRPC code generation. Obtain a valid API key from the server administrator.
- **Usage**:
  ```csharp
  // Replace {SERVER_ADDRESS} with the actual server address (e.g., https://localhost:5001)
  using var client = new GddsClient("{SERVER_ADDRESS}", "valid-api-key-123");
  string clientId = await client.GetClientIdAsync();
  Console.WriteLine($"Client ID: {clientId}");
  var receiveTask = client.StartReceivingAsync();
  Console.WriteLine("Press Enter to terminate...");
  Console.ReadLine();
  await client.TerminateAsync();
  await receiveTask;
  ```

### Java Client

- **Source**: [GddsClient.java](https://github.com/your-organization/gdds-streaming/blob/main/GddsStreaming.Client.Java/src/main/java/com/gddsstreaming/client/GddsClient.java) (replace with actual path).
- **Key Components**:
  - `GddsClient`: Manages the gRPC channel, stubs, client ID, API key, and subscription lifecycle.
  - `ApiKeyClientInterceptor`: Attaches the `x-api-key` header to all requests.
  - `getClientId`: Requests a client ID using a blocking stub.
  - `startReceiving`: Subscribes to the GDDS stream using an async stub and `StreamObserver`.
  - `terminate`: Sends a termination request and signals completion.
  - `VehicleType`: Enum for transportation modes.
- **Dependencies**:
  - Maven dependencies: `io.grpc:grpc-netty`, `io.grpc:grpc-protobuf`, `io.grpc:grpc-stub` (version 1.66.0 or later).
  - Generate gRPC code using `protobuf-maven-plugin`.
- **Setup**:
  ```xml
  <dependency>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-netty</artifactId>
      <version>1.66.0</version>
  </dependency>
  <dependency>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-protobuf</artifactId>
      <version>1.66.0</version>
  </dependency>
  <dependency>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-stub</artifactId>
      <version>1.66.0</version>
  </dependency>
  ```
  ```bash
  mvn clean install
  ```
  Obtain a valid API key from the server administrator.
- **Usage**:
  ```java
  // Replace {SERVER_ADDRESS} with the actual server address (e.g., localhost:50051)
  GddsClient client = new GddsClient("{SERVER_ADDRESS}", "valid-api-key-123");
  String clientId = client.getClientId();
  System.out.println("Client ID: " + clientId);
  new Thread(client::startReceiving).start();
  System.out.println("Press Enter to terminate...");
  new Scanner(System.in).nextLine();
  client.terminate();
  client.awaitTermination();
  ```

### Python Client

- **Source**: [gdds_client.py](https://github.com/your-organization/gdds-streaming/blob/main/GddsStreaming.Client.Python/gdds_client.py) (replace with actual path).
- **Key Components**:
  - `GddsClient`: Manages the gRPC channel, stub, client ID, API key, and subscription lifecycle.
  - `ApiKeyClientInterceptor`: Attaches the `x-api-key` header to all requests.
  - `get_client_id`: Requests a client ID.
  - `start_receiving`: Subscribes to the GDDS stream and processes messages.
  - `terminate`: Sends a termination request and stops the stream.
  - `VehicleType`: Static class for transportation modes.
- **Dependencies**:
  - Python packages: `grpcio`, `grpcio-tools` (version 1.66.0 or later).
  - Generate gRPC code using:
    ```bash
    python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. gdds_streaming.proto
    ```
- **Setup**:
  ```bash
  pip install grpcio grpcio-tools
  python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. gdds_streaming.proto
  ```
  Obtain a valid API key from the server administrator.
- **Usage**:
  ```python
  # Replace {SERVER_ADDRESS} with the actual server address (e.g., localhost:50051)
  with GddsClient("{SERVER_ADDRESS}", "valid-api-key-123") as client:
      client_id = client.get_client_id()
      print(f"Client ID: {client_id}")
      with ThreadPoolExecutor(max_workers=1) as executor:
          future = executor.submit(client.start_receiving)
          print("Press Enter to terminate...")
          input()
          client.terminate()
          future.result()
  ```

## Setup

### Prerequisites

- **C#**: .NET SDK 6.0+, NuGet packages (`Grpc.Net.Client`, `Google.Protobuf`, `Grpc.Tools`).
- **Java**: JDK 11+, Maven, gRPC dependencies (`io.grpc:grpc-netty`, `io.grpc:grpc-protobuf`, `io.grpc:grpc-stub`).
- **Python**: Python 3.7+, `grpcio`, `grpcio-tools`.
- **Proto File**: Include `gdds_streaming.proto` in each client project for gRPC code generation.
- **API Key**: Obtain a valid API key (e.g., `valid-api-key-123`) from the server administrator.
- **GitHub Repository**: Clone from [GddsStreaming Repository](https://github.com/your-organization/gdds-streaming) (replace with actual URL).

### Configuration

- **Server Address**: Set the server address to `{SERVER_ADDRESS}` and replace it with the actual server address (e.g., `https://localhost:5001` for C#, `localhost:50051` for Java/Python).
- **API Key**: Pass a valid API key to the client constructor. Contact the server administrator for the key.
- **SSL/TLS**: Configure SSL for production (C#: `GrpcChannelOptions`; Java: `useTransportSecurity`; Python: `ssl_channel_credentials`).

## Usage

### Client Workflow

1. **Initialize Client**: Create a client instance with the server address and a valid API key.
2. **Request Client ID**: Call `GetClientIdAsync` (C#), `getClientId` (Java), or `get_client_id` (Python) to obtain a unique ID.
3. **Subscribe**: Call `StartReceivingAsync` (C#), `startReceiving` (Java), or `start_receiving` (Python) to join the GDDS stream.
4. **Receive Messages**: Process `GddsMessage` objects containing IP, VehicleNumber, Latitude, Longitude, Speed, Bearing, TransitMode, Timestamp, ServerTimestamp, and Message.
5. **Terminate**: Call `TerminateAsync` (C#), `terminate` (Java), or `terminate` (Python) to end the subscription.

### Error Handling

- **Invalid Client ID**: Clients handle `Unauthenticated` errors for invalid or missing client IDs (e.g., "Invalid or unauthorized client ID: {clientId}").
- **Invalid API Key**: Clients handle `Unauthenticated` errors for missing or invalid API keys (e.g., "API key is missing in request headers" or "Invalid API key: {key}").
- **Duplicate Subscription**: Clients handle `AlreadyExists` errors for duplicate subscriptions (e.g., "Client ID {clientId} is already subscribed").
- **Cancellation**: Clients handle stream cancellation gracefully.
- **Resource Cleanup**: C# uses `Dispose`, Java uses `CountDownLatch`, Python uses context manager for cleanup.

## API Key Authentication

Clients use gRPC interceptors to attach an API key to all requests via the `x-api-key` metadata header, validated by the server’s `ApiKeyInterceptor`:
- **C#**: `ApiKeyClientInterceptor` adds the API key to the `Metadata` object for unary and streaming calls.
- **Java**: `ApiKeyClientInterceptor` uses `Metadata.Key` to attach the API key.
- **Python**: `ApiKeyClientInterceptor` implements `UnaryUnaryClientInterceptor` and `StreamUnaryClientInterceptor` to append the API key.
- **Setup**: Initialize the client with a valid API key (e.g., `valid-api-key-123`) obtained from the server administrator.
- **Error Handling**: Invalid or missing API keys result in `Unauthenticated` errors with server-provided details (e.g., "Invalid API key: {key}").

Example interceptor code is available in the repository: [GddsStreaming Repository](https://github.com/your-organization/gdds-streaming) (replace with actual URL).

## Security Considerations

- **Client ID Validation**: Clients must use a server-issued client ID to subscribe.
- **API Key Authentication**: Clients must provide a valid API key, validated by the server’s interceptor.
- **SSL/TLS**: Use SSL/TLS in production for secure communication.
- **Enhancements**: Consider API key rotation or secure key distribution for enhanced security.

## Testing

Unit tests, including API key authentication and duplicate subscription handling, are available at: [Tests](https://github.com/your-organization/gdds-streaming/tree/main/Tests/Client) (replace with actual path).

## Deployment

Clients can be run standalone or integrated into larger systems. Ensure the server is running, accessible, and configured with matching API keys.

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](https://github.com/your-organization/gdds-streaming/blob/main/CONTRIBUTING.md) for guidelines (replace with actual path).

## License

Licensed under the MIT License. See [LICENSE](https://github.com/your-organization/gdds-streaming/blob/main/LICENSE) for details (replace with actual path).