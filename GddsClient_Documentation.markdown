# GddsClient Technical Documentation

## Overview

The `GddsClient` is a client implementation for interacting with the `GddsStreamService`, a gRPC-based server that streams GPS Data Distribution System (GDDS) records, a vehicle tracking system based on the NMEA industry standard. The client supports subscribing to real-time vehicle data streams and terminating specific connections. It is designed for a small number of clients (e.g., transit agencies or vendors), each with a unique API key and potentially multiple indefinite connections from different applications (e.g., mobile apps, web apps, backend services). Each connection is tracked using a client-generated `connectionToken` (e.g., UUIDv4), validated for uniqueness by the server. The client authenticates with the server using an API key provided in the `x-api-key` header, enabling transit agencies and developers to build applications for vehicle tracking, navigation, and trip prediction.

Source code for client implementations is available at:
- C#: [GddsClient.cs](https://github.com/your-organization/gdds-client-csharp) (replace with actual URL)
- Java: [GddsClient.java](https://github.com/your-organization/gdds-client-java) (replace with actual URL)
- Python: [gdds_client.py](https://github.com/your-organization/gdds-client-python) (replace with actual URL)

## gRPC Overview

[gRPC](https://grpc.io/) is a high-performance, open-source remote procedure call (RPC) framework developed by Google. It uses Protocol Buffers (Protobuf) for defining service contracts and HTTP/2 for efficient, bidirectional communication. gRPC is ideal for the `GddsClient` and `GddsStreamService` architecture due to its key features:

- **High Performance**: gRPC leverages HTTP/2 for multiplexing, header compression, and binary framing, reducing latency and improving throughput compared to traditional REST APIs. This is critical for real-time streaming of `GddsMessage` objects containing vehicle tracking data.
- **Bidirectional Streaming**: gRPC supports server-side streaming, enabling the `GddsStreamService` to continuously push `GddsMessage` updates to clients without repeated polling, which is ideal for real-time vehicle tracking applications.
- **Strongly Typed Contracts**: Using Protocol Buffers, gRPC enforces strict service definitions (e.g., `gdds_streaming.proto`), ensuring type safety and consistency across client implementations in C#, Java, and Python.
- **Scalability**: gRPC’s efficient use of HTTP/2 connections supports multiple concurrent streams, allowing clients to maintain indefinite connections with unique `connectionToken` values while minimizing server resource usage.
- **Cross-Platform Support**: gRPC provides native support for multiple languages (C#, Java, Python, etc.), making it suitable for diverse client applications (mobile apps, web apps, backend services).
- **Security**: gRPC uses HTTPS (port 443) with SSL/TLS for secure communication, ensuring that API keys and `GddsMessage` data are encrypted during transmission.

For more details, refer to the official [gRPC Documentation](https://grpc.io/docs/) and [Protocol Buffers Documentation](https://developers.google.com/protocol-buffers/docs/overview).

### Why gRPC is Ideal for This Architecture

The `GddsStreamService` and `GddsClient` architecture benefits from gRPC due to the following reasons:
- **Real-Time Data Streaming**: The server-side streaming capability of gRPC allows continuous delivery of `GddsMessage` objects (e.g., vehicle location, speed, and heading) to clients, which is essential for real-time applications like vehicle tracking and trip prediction.
- **Efficient Resource Utilization**: HTTP/2 multiplexing enables multiple client connections (tracked via `connectionToken`) to share a single TCP connection, reducing overhead and supporting a small number of clients with multiple indefinite connections.
- **Reliable Authentication**: gRPC’s metadata support allows secure transmission of API keys in the `x-api-key` header, ensuring robust client authentication.
- **Error Handling and Retries**: gRPC’s status codes (e.g., `AlreadyExists`, `Unauthenticated`, `InvalidArgument`) provide clear error semantics, enabling clients to handle issues like duplicate `connectionToken` values or invalid API keys with retries and exponential backoff.
- **Interoperability**: The use of Protocol Buffers ensures that the `GddsMessage` structure and service methods are consistently implemented across C#, Java, and Python clients, simplifying development and maintenance.
- **Low Latency**: Binary serialization with Protocol Buffers and HTTP/2’s performance optimizations minimize latency, critical for delivering timely vehicle data to transit agencies and end-users.

By leveraging gRPC, the `GddsClient` achieves efficient, secure, and scalable communication with the `GddsStreamService`, making it an ideal choice for real-time vehicle tracking systems.

## Proto File

The client uses the following Protocol Buffers file to define the gRPC service: [gdds_streaming.proto](https://github.com/your-organization/gdds-streaming/blob/main/GddsStreaming/Protos/gdds_streaming.proto) (replace with actual path).

```proto
syntax = "proto3";

package GddsStreaming;

message GddsMessage {
    string veh = 1; // Vehicle number
    int64 tm = 2;  // Timestamp
    double lat = 3; // Latitude
    double lon = 4; // Longitude
    double hdg = 5; // Heading
    double spd = 6; // Speed
    int32 trnTyp = 7; // Transit mode
    int64 svrTm = 8; // Server timestamp
}

message SubscriptionRequest {
    string connectionToken = 1; // Client-provided unique token (e.g., UUIDv4)
}

message TerminationRequest {
    string connectionToken = 1; // Client-provided token to identify connection
}

service GddsStreamService {
    rpc Subscribe(SubscriptionRequest) returns (stream GddsMessage);
    rpc Terminate(TerminationRequest) returns (TerminationResponse);
}

message TerminationResponse {
    bool success = 1;
}
```

## Client Implementations

The client is implemented in multiple languages to support various platforms. Each implementation generates a unique `connectionToken` (UUIDv4 recommended) for each connection, handles server-side uniqueness validation, and persists tokens for indefinite connections.

### C# Client

- **Source**: [GddsClient.cs](https://github.com/your-organization/gdds-client-csharp/blob/main/GddsClient.cs) (replace with actual path)
- **Dependencies**:
  - NuGet packages: `Grpc.Net.Client`, `Google.Protobuf`, `Grpc.Tools`
- **Setup**:
  ```bash
  dotnet add package Grpc.Net.Client
  dotnet add package Google.Protobuf
  dotnet add package Grpc.Tools
  dotnet restore
  dotnet build
  ```
  Include `gdds_streaming.proto` in the project for gRPC code generation.
- **Example**:
  ```csharp
  using Grpc.Core;
  using Grpc.Net.Client;
  using System;
  using System.IO;
  using System.Threading.Tasks;

  namespace GddsClient
  {
      class Program
      {
          static async Task Main(string[] args)
          {
              string serverAddress = "{SERVER_ADDRESS}"; // Provided by service administrator
              string apiKey = "valid-api-key-123";
              string tokenFile = "connection_token.txt";
              string connectionToken;

              if (File.Exists(tokenFile))
              {
                  connectionToken = File.ReadAllText(tokenFile).Trim();
                  Console.WriteLine($"Loaded connection token: {connectionToken}");
              }
              else
              {
                  connectionToken = Guid.NewGuid().ToString();
                  File.WriteAllText(tokenFile, connectionToken);
                  Console.WriteLine($"Generated new connection token: {connectionToken}");
              }

              int maxRetries = 3;
              int attempt = 0;
              while (attempt < maxRetries)
              {
                  try
                  {
                      using var channel = GrpcChannel.ForAddress($"https://{serverAddress}", new GrpcChannelOptions());
                      var client = new GddsStreaming.GddsStreamService.GddsStreamServiceClient(channel);
                      var metadata = new Metadata { { "x-api-key", apiKey } };

                      var request = new SubscriptionRequest { ConnectionToken = connectionToken };
                      using var call = client.Subscribe(request, metadata);
                      Console.WriteLine($"Subscribed with token: {connectionToken}");
                      await foreach (var message in call.ResponseStream.ReadAllAsync())
                      {
                          Console.WriteLine($"Received: Vehicle={message.Veh}, Lat={message.Lat}, Lon={message.Lon}, Time={message.Tm}");
                      }
                      break;
                  }
                  catch (RpcException ex) when (ex.StatusCode == StatusCode.AlreadyExists)
                  {
                      Console.WriteLine($"Duplicate token {connectionToken}. Retrying with new token ({attempt + 1}/{maxRetries}).");
                      connectionToken = Guid.NewGuid().ToString();
                      File.WriteAllText(tokenFile, connectionToken);
                      attempt++;
                      continue;
                  }
                  catch (RpcException ex)
                  {
                      Console.WriteLine($"Error: {ex.Status.Detail}");
                      if (ex.StatusCode == StatusCode.Unavailable || ex.StatusCode == StatusCode.Internal)
                      {
                          int delay = (int)Math.Pow(2, attempt) * 1000;
                          Console.WriteLine($"Retrying in {delay}ms ({attempt + 1}/{maxRetries})...");
                          await Task.Delay(delay);
                          attempt++;
                          continue;
                      }
                      break;
                  }
              }

              if (attempt >= maxRetries)
              {
                  Console.WriteLine("Max retries reached. Exiting.");
                  return;
              }

              try
              {
                  using var channel = GrpcChannel.ForAddress($"https://{serverAddress}", new GrpcChannelOptions());
                  var client = new GddsStreaming.GddsStreamService.GddsStreamServiceClient(channel);
                  var metadata = new Metadata { { "x-api-key", apiKey } };
                  var termRequest = new TerminationRequest { ConnectionToken = connectionToken };
                  var termResponse = await client.TerminateAsync(termRequest, metadata);
                  Console.WriteLine($"Termination success: {termResponse.Success}");
                  if (termResponse.Success)
                  {
                      File.Delete(tokenFile);
                  }
              }
              catch (RpcException ex)
              {
                  Console.WriteLine($"Termination error: {ex.Status.Detail}");
              }
          }
      }
  }
  ```

### Java Client

- **Source**: [GddsClient.java](https://github.com/your-organization/gdds-client-java/blob/main/GddsClient.java) (replace with actual path)
- **Dependencies**:
  - Maven dependencies: `io.grpc:grpc-netty`, `io.grpc:grpc-protobuf`, `io.grpc:grpc-stub`
- **Setup**:
  ```xml
  <dependencies>
      <dependency>
          <groupId>io.grpc</groupId>
          <artifactId>grpc-netty</artifactId>
          <version>1.65.0</version>
      </dependency>
      <dependency>
          <groupId>io.grpc</groupId>
          <artifactId>grpc-protobuf</artifactId>
          <version>1.65.0</version>
      </dependency>
      <dependency>
          <groupId>io.grpc</groupId>
          <artifactId>grpc-stub</artifactId>
          <version>1.65.0</version>
      </dependency>
  </dependencies>
  ```
  Include `gdds_streaming.proto` for gRPC code generation using `protoc`.
- **Example**:
  ```java
  import io.grpc.ManagedChannel;
  import io.grpc.ManagedChannelBuilder;
  import io.grpc.Metadata;
  import io.grpc.StatusRuntimeException;
  import java.io.FileReader;
  import java.io.FileWriter;
  import java.util.UUID;
  import java.util.concurrent.TimeUnit;

  public class GddsClient {
      public static void main(String[] args) throws Exception {
          String serverAddress = "{SERVER_ADDRESS}";
          String apiKey = "valid-api-key-123";
          String tokenFile = "connection_token.txt";
          String connectionToken;

          try (FileReader reader = new FileReader(tokenFile)) {
              char[] buffer = new char[36];
              reader.read(buffer);
              connectionToken = new String(buffer).trim();
              System.out.println("Loaded connection token: " + connectionToken);
          } catch (Exception e) {
              connectionToken = UUID.randomUUID().toString();
              try (FileWriter writer = new FileWriter(tokenFile)) {
                  writer.write(connectionToken);
              }
              System.out.println("Generated new connection token: " + connectionToken);
          }

          int maxRetries = 3;
          int attempt = 0;
          ManagedChannel channel = null;
          while (attempt < maxRetries) {
              try {
                  channel = ManagedChannelBuilder.forTarget(serverAddress)
                      .useTransportSecurity()
                      .build();
                  GddsStreamServiceGrpc.GddsStreamServiceBlockingStub client = GddsStreamServiceGrpc.newBlockingStub(channel);
                  Metadata metadata = new Metadata();
                  metadata.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), apiKey);

                  SubscriptionRequest request = SubscriptionRequest.newBuilder()
                      .setConnectionToken(connectionToken)
                      .build();
                  Iterator<GddsMessage> messages = client.withCallCredentials(new CallCredentials() {
                      @Override
                      public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier) {
                          applier.apply(metadata);
                      }
                  }).subscribe(request);
                  System.out.println("Subscribed with token: " + connectionToken);
                  while (messages.hasNext()) {
                      GddsMessage message = messages.next();
                      System.out.println("Received: Vehicle=" + message.getVeh() + 
                          ", Lat=" + message.getLat() + ", Lon=" + message.getLon() + ", Time=" + message.getTm());
                  }
                  break;
              } catch (StatusRuntimeException e) {
                  if (e.getStatus().getCode() == io.grpc.Status.Code.ALREADY_EXISTS) {
                      System.out.println("Duplicate token " + connectionToken + ". Retrying with new token (" + (attempt + 1) + "/" + maxRetries + ").");
                      connectionToken = UUID.randomUUID().toString();
                      try (FileWriter writer = new FileWriter(tokenFile)) {
                          writer.write(connectionToken);
                      }
                      attempt++;
                      continue;
                  } else if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE || 
                             e.getStatus().getCode() == io.grpc.Status.Code.INTERNAL) {
                      int delay = (int) Math.pow(2, attempt) * 1000;
                      System.out.println("Connection error: " + e.getStatus().getDescription() + 
                          ". Retrying in " + delay + "ms (" + (attempt + 1) + "/" + maxRetries + ")...");
                      Thread.sleep(delay);
                      attempt++;
                      continue;
                  } else {
                      System.out.println("Error: " + e.getStatus().getDescription());
                      break;
                  }
              } finally {
                  if (channel != null) {
                      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                  }
              }
          }

          if (attempt >= maxRetries) {
              System.out.println("Max retries reached. Exiting.");
              return;
          }

          try {
              channel = ManagedChannelBuilder.forTarget(serverAddress)
                  .useTransportSecurity()
                  .build();
              GddsStreamServiceGrpc.GddsStreamServiceBlockingStub client = GddsStreamServiceGrpc.newBlockingStub(channel);
              Metadata metadata = new Metadata();
              metadata.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), apiKey);

              TerminationRequest termRequest = TerminationRequest.newBuilder()
                  .setConnectionToken(connectionToken)
                  .build();
              TerminationResponse termResponse = client.withCallCredentials(new CallCredentials() {
                  @Override
                  public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier) {
                      applier.apply(metadata);
                  }
              }).terminate(termRequest);
              System.out.println("Termination success: " + termResponse.getSuccess());
              if (termResponse.getSuccess()) {
                  new java.io.File(tokenFile).delete();
              }
          } catch (StatusRuntimeException e) {
              System.out.println("Termination error: " + e.getStatus().getDescription());
          } finally {
              if (channel != null) {
                  channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
              }
          }
      }
  }
  ```

### Python Client

- **Source**: [gdds_client.py](https://github.com/your-organization/gdds-client-python/blob/main/gdds_client.py) (replace with actual path)
- **Dependencies**:
  - Python packages: `grpcio`, `grpcio-tools`
- **Setup**:
  ```bash
  pip install grpcio grpcio-tools
  ```
  Generate Python gRPC code from `gdds_streaming.proto`:
  ```bash
  python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. gdds_streaming.proto
  ```
- **Example**:
  ```python
  import uuid
  import grpc
  import time
  from gdds_streaming_pb2 import SubscriptionRequest, TerminationRequest
  from gdds_streaming_pb2_grpc import GddsStreamServiceStub

  def main():
      server_address = "{SERVER_ADDRESS}"
      api_key = "valid-api-key-123"
      token_file = "connection_token.txt"
      connection_token = None

      try:
          with open(token_file, "r") as f:
              connection_token = f.read().strip()
              print(f"Loaded connection token: {connection_token}")
      except FileNotFoundError:
          connection_token = str(uuid.uuid4())
          with open(token_file, "w") as f:
              f.write(connection_token)
          print(f"Generated new connection token: {connection_token}")

      max_retries = 3
      attempt = 0
      while attempt < max_retries:
          try:
              with grpc.secure_channel(server_address, grpc.ssl_channel_credentials()) as channel:
                  stub = GddsStreamServiceStub(channel)
                  metadata = [("x-api-key", api_key)]

                  request = SubscriptionRequest(connectionToken=connection_token)
                  try:
                      print(f"Subscribed with token: {connection_token}")
                      for message in stub.Subscribe(request, metadata=metadata):
                          print(f"Received: Vehicle={message.veh}, Lat={message.lat}, Lon={message.lon}, Time={message.tm}")
                  except grpc.RpcError as e:
                      if e.code() == grpc.StatusCode.ALREADY_EXISTS:
                          print(f"Duplicate token {connection_token}. Retrying with new token ({attempt + 1}/{max_retries}).")
                          connection_token = str(uuid.uuid4())
                          with open(token_file, "w") as f:
                              f.write(connection_token)
                          attempt += 1
                          continue
                      elif e.code() in (grpc.StatusCode.UNAVAILABLE, grpc.StatusCode.INTERNAL):
                          delay = 2 ** attempt
                          print(f"Connection error: {e.details()}. Retrying in {delay}s ({attempt + 1}/{max_retries})...")
                          time.sleep(delay)
                          attempt += 1
                          continue
                      else:
                          print(f"Error: {e.details()}")
                          break
                  break
          except grpc.RpcError as e:
              delay = 2 ** attempt
              print(f"Channel error: {e.details()}. Retrying in {delay}s ({attempt + 1}/{max_retries})...")
              time.sleep(delay)
              attempt += 1

      if (attempt >= maxRetries):
          print("Max retries reached. Exiting.")
          return

      try:
          with grpc.secure_channel(server_address, grpc.ssl_channel_credentials()) as channel:
              stub = GddsStreamServiceStub(channel)
              metadata = [("x-api-key", api_key)]
              term_request = TerminationRequest(connectionToken=connection_token)
              response = stub.Terminate(term_request, metadata=metadata)
              print(f"Termination success: {response.success}")
              if response.success:
                  import os
                  os.remove(token_file)
      except grpc.RpcError as e:
          print(f"Termination error: {e.details()}")

  if __name__ == "__main__":
      main()
  ```

## Setup

### Prerequisites

- **C#**: .NET SDK 9.0 (latest stable release), NuGet packages (`Grpc.Net.Client` v2.65.0, `Google.Protobuf` v3.27.0, `Grpc.Tools` v2.65.0)
- **Java**: JDK 21 (latest LTS release), Maven, dependencies (`io.grpc:grpc-netty` v1.65.0, `io.grpc:grpc-protobuf` v1.65.0, `io.grpc:grpc-stub` v1.65.0)
- **Python**: Python 3.12 (latest stable release), packages (`grpcio` v1.65.0, `grpcio-tools` v1.65.0)
- **Proto File**: Include `gdds_streaming.proto` in the client project for gRPC code generation
- **Server Configuration**:
  - **Server Address**: Replace `{SERVER_ADDRESS}` with the service address provided by the administrator
  - **API Key**: Obtain a unique API key from the service administrator
- **GitHub Repositories**:
  - C#: [GddsClient C# Repository](https://github.com/your-organization/gdds-client-csharp) (replace with actual URL)
  - Java: [GddsClient Java Repository](https://github.com/your-organization/gdds-client-java) (replace with actual URL)
  - Python: [GddsClient Python Repository](https://github.com/your-organization/gdds-client-python) (replace with actual URL)

### Configuration

- **Server Address**: Set `{SERVER_ADDRESS}` to the service address provided by the administrator (e.g., a domain with port 443 for HTTPS).
- **API Key**: Include the unique API key in the `x-api-key` header for all gRPC calls (`Subscribe`, `Terminate`). Contact the service administrator to obtain your API key.
- **Connection Token**:
  - Generate a unique `connectionToken` (UUIDv4 recommended) for each connection using standard libraries:
    - C#: `System.Guid.NewGuid().ToString()`
    - Java: `java.util.UUID.randomUUID().toString()`
    - Python: `uuid.uuid4().__str__()`
  - Persist the `connectionToken` (e.g., in a configuration file or database) to support reconnection or explicit termination for indefinite connections.
  - If the server returns an `AlreadyExists` error (e.g., "Connection token {connectionToken} is already in use"), generate a new token and retry (up to 3 attempts recommended).
- **SSL/TLS**: Use HTTPS (port 443) for secure communication with the server.

## Connection Token and API Key Authentication

The client uses API key authentication and client-generated connection tokens to support a small number of clients with multiple indefinite connections:
- **API Key Authentication**:
  - Each client is assigned a unique API key, included in the `x-api-key` header for all gRPC calls (`Subscribe`, `Terminate`).
  - Ensures only authorized clients can access the service.
  - Obtain the API key from the service administrator.
- **Connection Token**:
  - Each connection (e.g., from a mobile app, web app, or backend service) must provide a unique `connectionToken` (UUIDv4 recommended) in `SubscriptionRequest` and `TerminationRequest`.
  - The server validates `connectionToken` uniqueness, rejecting duplicates with an `AlreadyExists` error.
  - **Purpose and Benefits**:
    - **Multi-Connection Tracking**: Allows each client to maintain multiple indefinite connections using the same API key, with each connection tracked via a unique `connectionToken`.
    - **Explicit Termination**: Enables clients to terminate specific connections using the `Terminate` method with the `connectionToken`.
    - **Auditing**: The server logs connection-specific events using `connectionToken` and API key.
    - **Conflict Prevention**: Server-side validation ensures no duplicate `connectionToken` values.
    - **Resource Management**: The server’s cleanup task removes stale connections.
  - **Configuration**:
    - Generate a `connectionToken` using UUIDv4 to minimize collision risk.
    - Persist the `connectionToken` to reuse for reconnection or termination.
    - On `AlreadyExists` errors, generate a new `connectionToken` and retry (up to 3 attempts).
  - **Considerations**: Clients are responsible for generating unique tokens and handling retries. UUIDv4 is recommended for low collision probability.

## Connection Token File Storage

In the provided example code, the `connectionToken` is stored in a text file named `connection_token.txt` to persist it between application runs. This section explains the purpose and usage of this file.

- **Purpose**:
  - **Persistence**: The `connection_token.txt` file stores the `connectionToken` (a UUIDv4 string) to ensure it can be reused for reconnection or termination, maintaining continuity for long-lived connections to the `GddsStreamService`.
  - **Reconnection**: When the client restarts, it reads the token from the file to resume an existing connection instead of creating a new one.
  - **Termination**: The stored token is used to explicitly terminate a specific connection via the `Terminate` method without affecting other connections.
  - **Error Handling**: If the server rejects a token with an `AlreadyExists` error, the client generates a new token and updates the file.

- **Usage in Code**:
  - **Reading the Token**: The client checks if `connection_token.txt` exists and reads the stored `connectionToken` if available.
  - **Generating a New Token**: If the file does not exist, a new UUIDv4 token is generated (e.g., using `Guid.NewGuid()` in C#) and written to the file.
  - **Updating on Error**: On an `AlreadyExists` error, a new token is generated and the file is overwritten.
  - **Deleting on Termination**: If the `Terminate` method succeeds, the file is deleted to clean up.

- **Examples**:
  - **C#**:
    ```csharp
    string tokenFile = "connection_token.txt";
    string connectionToken;
    if (File.Exists(tokenFile))
    {
        connectionToken = File.ReadAllText(tokenFile).Trim();
        Console.WriteLine($"Loaded connection token: {connectionToken}");
    }
    else
    {
        connectionToken = Guid.NewGuid().ToString();
        File.WriteAllText(tokenFile, connectionToken);
        Console.WriteLine($"Generated new connection token: {connectionToken}");
    }
    ```
  - **Java**:
    ```java
    String tokenFile = "connection_token.txt";
    String connectionToken;
    try (FileReader reader = new FileReader(tokenFile)) {
        char[] buffer = new char[36];
        reader.read(buffer);
        connectionToken = new String(buffer).trim();
        System.out.println("Loaded connection token: " + connectionToken);
    } catch (Exception e) {
        connectionToken = UUID.randomUUID().toString();
        try (FileWriter writer = new FileWriter(tokenFile)) {
            writer.write(connectionToken);
        }
        System.out.println("Generated new connection token: " + connectionToken);
    }
    ```
  - **Python**:
    ```python
    token_file = "connection_token.txt"
    connection_token = None
    try:
        with open(token_file, "r") as f:
            connection_token = f.read().strip()
            print(f"Loaded connection token: {connection_token}")
    except FileNotFoundError:
        connection_token = str(uuid.uuid4())
        with open(token_file, "w") as f:
            f.write(connection_token)
        print(f"Generated new connection token: {connection_token}")
    ```

- **Alternatives**:
  - **Database**: Store the `connectionToken` in a database for better scalability and durability in production environments.
  - **Configuration File**: Use a JSON or XML configuration file to store the token alongside other settings.
  - **Environment Variables**: Store the token in an environment variable for short-lived applications (less suitable for reconnection).
  - **In-Memory Storage**: Keep the token in memory for temporary connections, though this does not persist across restarts.

- **Best Practices**:
  - **Secure Storage**: Store `connection_token.txt` in a secure location with restricted access to prevent unauthorized modifications.
  - **Unique File Names**: For multiple clients on the same machine, use unique file names (e.g., appending a client ID) to avoid conflicts.
  - **Backup**: Consider backing up the token file or using a more robust storage solution in production to prevent data loss.
  - **Logging**: Log file operations (e.g., read, write, delete) to aid debugging, as shown in the example code.

The use of `connection_token.txt` in the examples is a simple, effective way to persist the `connectionToken` for small-scale or testing scenarios, but consider alternative storage mechanisms for production applications.

## Usage

### Client Workflow

1. **Generate Connection Token**: Create a unique `connectionToken` (e.g., UUIDv4) and persist it.
2. **Configure API Key**: Include the API key in the `x-api-key` header.
3. **Subscribe to Stream**:
   - Call `Subscribe` with a `SubscriptionRequest` containing the `connectionToken`.
   - Handle `AlreadyExists` errors by generating a new `connectionToken` and retrying.
   - Process `GddsMessage` objects containing vehicle data (VehicleNumber, Timestamp, Latitude, Longitude, Heading, Speed, TransitMode, ServerTimestamp).
4. **Handle Indefinite Connections**:
   - Persist the `connectionToken` for long-lived connections.
   - Implement reconnection logic with exponential backoff.
5. **Terminate Connection**:
   - Call `Terminate` with a `TerminationRequest` containing the `connectionToken`.
6. **Error Handling**:
   - Handle `InvalidArgument` errors for missing `connectionToken`.
   - Handle `AlreadyExists` errors by retrying with a new token.
   - Handle `Unauthenticated` errors for invalid API keys.
   - Implement reconnection for network or server errors.

### Error Handling

- **Invalid Connection Token**: Handle `InvalidArgument` errors for missing `connectionToken`.
- **Duplicate Connection Token**: Handle `AlreadyExists` errors by generating a new UUIDv4 and retrying.
- **Invalid API Key**: Handle `Unauthenticated` errors by verifying the API key.
- **Connection Errors**: Implement exponential backoff for network or server errors.
- **Server Disconnection**: Reconnect using the same or a new `connectionToken`.

## Security Considerations

- **Connection Token Uniqueness**: Use UUIDv4 to minimize collision risk. The server rejects duplicates.
- **API Key Security**: Store the API key securely and include it in the `x-api-key` header.
- **SSL/TLS**: Use HTTPS (port 443) for secure communication.
- **Reconnection**: Persist `connectionToken` to maintain continuity.
- **Monitoring**: Log errors (e.g., duplicate tokens, invalid API keys) for debugging.

## Testing

Unit tests for client implementations are available at:
- C#: [Tests](https://github.com/your-organization/gdds-client-csharp/tree/main/Tests) (replace with actual path)
- Java: [Tests](https://github.com/your-organization/gdds-client-java/tree/main/Tests) (replace with actual path)
- Python: [Tests](https://github.com/your-organization/gdds-client-python/tree/main/Tests) (replace with actual path)

### Test Scenarios
- **Indefinite Connections**: Verify long-lived connections with stable streaming.
- **Multi-Connection Tracking**: Test multiple connections per client with unique `connectionToken` values.
- **Connection Token Uniqueness**: Simulate duplicate `connectionToken` values, verifying retries.
- **API Key Validation**: Test valid and invalid API keys.
- **Concurrent Subscriptions**: Run multiple client instances concurrently.
- **Reconnection**: Simulate network interruptions and verify reconnection.
- **Termination**: Terminate a specific connection without affecting others.
- **Error Handling**: Test `InvalidArgument`, `AlreadyExists`, and `Unauthenticated` errors.

## Deployment

1. **Build Client**:
   - **C#**: `dotnet build`
   - **Java**: `mvn clean install`
   - **Python**: Install dependencies and generate gRPC code
2. **Configure Client**:
   - Set `{SERVER_ADDRESS}` to the service address.
   - Configure the API key.
   - Generate and persist a `connectionToken`.
3. **Run Client**:
   - **C#**: `dotnet run`
   - **Java**: `java -jar target/gdds-client.jar`
   - **Python**: `python gdds_client.py`
4. **Verify**:
   - Confirm receipt of `GddsMessage` objects.
   - Test termination using the `connectionToken`.
   - Monitor logs for errors.

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](https://github.com/your-organization/gdds-client/blob/main/CONTRIBUTING.md) for guidelines (replace with actual path).

## License

Licensed under the MIT License. See [LICENSE](https://github.com/your-organization/gdds-client/blob/main/LICENSE) for details (replace with actual path).