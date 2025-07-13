# GddsClient Technical Documentation

## Overview

The `GddsClient` implementations in C#, Java, and Python provide client-side functionality to interact with the `GddsStreamService`, a gRPC-based server that streams GNSS Derived Data Stream (GDDS) records. The clients support requesting a unique client ID, subscribing to the GDDS stream, receiving messages, and terminating subscriptions. Each client is designed to handle errors gracefully and manage resources effectively.

The client implementations are hosted on GitHub at: [GddsStreaming Repository](https://github.com/strada-360/nextrip-gdds-client/tree/main/samples).

## Architecture

The clients follow a gRPC client-side streaming model, interacting with the `GddsStreamService` through three methods:
- `GenerateClientId`: Requests a unique client ID for access control.
- `Subscribe`: Subscribes to a stream of `GddsMessage` objects using the client ID.
- `Terminate`: Terminates the subscription and cleans up resources.

### Proto File

The clients use the same Protocol Buffers (`.proto`) file as the server, defining the service and message structures. The proto file is available at: [gdds.proto](https://github.com/strada-360/nextrip-gdds-client/blob/main/gdds.proto).

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

message Cache-Control: no-cache
Content-Type: application/octet-stream

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

- **Source**: [GddsClient.cs](https://github.com/strada-360/nextrip-gdds-client/blob/main/samples/GddsClient.cs).
- **Key Components**:
  - `GddsClient`: Manages the gRPC channel, client ID, and subscription lifecycle.
  - `GetClientIdAsync`: Requests a client ID from the server.
  - `StartReceivingAsync`: Subscribes to the GDDS stream and processes messages asynchronously.
  - `TerminateAsync`: Sends a termination request and cancels the stream.
  - `VehicleType`: Enum for transportation modes.
- **Dependencies**:
  - NuGet packages: `Grpc.Net.Client`, `Google.Protobuf`, `Grpc.Tools`.
- **Setup**:
  ```bash
  dotnet restore
  dotnet build
  ```
  Ensure the proto file is included and configured for gRPC code generation.
- **Usage**:
  ```csharp
  using var client = new GddsClient("https://localhost:5001");
  string clientId = await client.GetClientIdAsync();
  var receiveTask = client.StartReceivingAsync();
  Console.ReadKey();
  await client.TerminateAsync();
  await receiveTask;
  ```

### Java Client

- **Source**: [GddsClient.java](https://github.com/your-organization/gdds-streaming/blob/main/GddsStreaming.Client.Java/src/main/java/com/gddsstreaming/client/GddsClient.java) (replace with actual path).
- **Key Components**:
  - `GddsClient`: Manages the gRPC channel, stubs, and subscription lifecycle.
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
  <!-- Add grpc-protobuf and grpc-stub similarly -->
  ```
  ```bash
  mvn clean install
  ```
- **Usage**:
  ```java
  GddsClient client = new GddsClient("localhost:50051");
  String clientId = client.getClientId();
  new Thread(client::startReceiving).start();
  new Scanner(System.in).nextLine();
  client.terminate();
  client.awaitTermination();
  ```

### Python Client

- **Source**: [gdds_client.py](https://github.com/your-organization/gdds-streaming/blob/main/GddsStreaming.Client.Python/gdds_client.py) (replace with actual path).
- **Key Components**:
  - `GddsClient`: Manages the gRPC channel, stub, and subscription lifecycle.
  - `get_client_id`: Requests a client ID.
  - `start_receiving`: Subscribes to the GDDS stream and processes messages.
  - `terminate`: Sends a termination request and stops the stream.
  - `VehicleType`: Static class for transportation modes.
- **Dependencies**:
  - Python packages: `grpcio`, `grpcio-tools`.
  - Generate gRPC code using:
    ```bash
    python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. gdds_streaming.proto
    ```
- **Setup**:
  ```bash
  pip install grpcio grpcio-tools
  python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. gdds_streaming.proto
  ```
- **Usage**:
  ```python
  with GddsClient("localhost:50051") as client:
      client_id = client.get_client_id()
      with ThreadPoolExecutor(max_workers=1) as executor:
          future = executor.submit(client.start_receiving)
          input()
          client.terminate()
          future.result()
  ```

## Setup

### Prerequisites

- **C#**: .NET SDK 6.0+, NuGet packages (`Grpc.Net.Client`, `Google.Protobuf`, `Grpc.Tools`).
- **Java**: JDK 11+, Maven, gRPC Java dependencies.
- **Python**: Python 3.7+, `grpcio`, `grpcio-tools`.
- **Proto File**: Include `gdds_streaming.proto` in each client project and generate gRPC code.
- **GitHub Repository**: Clone from [GddsStreaming Repository](https://github.com/your-organization/gdds-streaming) (replace with actual URL).

### Configuration

- **Server Address**: Update the server address in each client (e.g., `https://localhost:5001` for C#, `localhost:50051` for Java/Python).
- **SSL/TLS**: Configure SSL for production (C#: use `GrpcChannelOptions`; Java: use `useTransportSecurity`; Python: use `ssl_channel_credentials`).

## Usage

### Client Workflow

1. **Request Client ID**: Call `GetClientIdAsync` (C#), `getClientId` (Java), or `get_client_id` (Python) to obtain a unique client ID.
2. **Subscribe**: Call `StartReceivingAsync` (C#), `startReceiving` (Java), or `start_receiving` (Python) to subscribe to the GDDS stream.
3. **Receive Messages**: Process incoming `GddsMessage` objects, which include IP, vehicle number, coordinates, speed, bearing, transit mode, timestamps, and message.
4. **Terminate**: Call `TerminateAsync` (C#), `terminate` (Java), or `terminate` (Python) to end the subscription.

### Error Handling

- **Invalid Client ID**: Clients handle `Unauthenticated` errors for invalid client IDs.
- **Cancellation**: Clients handle stream cancellation gracefully.
- **Resource Cleanup**: C# uses `Dispose`, Java uses `CountDownLatch`, Python uses context manager for cleanup.

## Security Considerations

- **Client ID Validation**: Clients must use a server-issued клиент ID to subscribe.
- **SSL/TLS**: Recommended for secure communication in production.
- **Enhancements**: Consider adding authentication for `GenerateClientId` calls.

## Testing

Unit tests for clients are available in the repository: [Tests](https://github.com/your-organization/gdds-streaming/tree/main/Tests/Client) (replace with actual path).

## Deployment

Clients can be run as standalone applications or integrated into larger systems. Ensure the server is running and accessible.

## Contributing

Contributions are welcome! See the [CONTRIBUTING.md](https://github.com/your-organization/gdds-streaming/blob/main/CONTRIBUTING.md) file for guidelines (replace with actual path).

## License

This project is licensed under the MIT License. See [LICENSE](https://github.com/your-organization/gdds-streaming/blob/main/LICENSE) for details (replace with actual path).
