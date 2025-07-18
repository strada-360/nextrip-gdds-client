package com.gddsstreaming.client;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * Client for interacting with the GddsStreamService to receive GPS Data Distribution System (GDDS) records.
 */
public class GddsClient {
    private final GddsStreamServiceGrpc.GddsStreamServiceStub asyncStub;
    private final GddsStreamServiceGrpc.GddsStreamServiceBlockingStub blockingStub;
    private String clientId;
    private final CountDownLatch latch = new CountDownLatch(1);

    public enum VehicleType {
        UNKNOWN(0),
        CAR(1),
        TRUCK(2),
        BUS(3),
        MOTORCYCLE(4);

        private final int value;

        VehicleType(int value) {
            this.value = value;
        }

        public static VehicleType valueOf(int value) {
            for (VehicleType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * Initializes the client with the server address and API key.
     *
     * @param serverAddress The server address (e.g., "localhost:50051").
     * @param apiKey The API key for authentication.
     */
    public GddsClient(String serverAddress, String apiKey) {
        if (serverAddress == null || serverAddress.isEmpty()) {
            throw new IllegalArgumentException("Server address cannot be null or empty");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }

        ManagedChannel channel = ManagedChannelBuilder.forTarget(serverAddress)
                .usePlaintext() // Use plaintext for simplicity; configure SSL for production
                .intercept(new ApiKeyClientInterceptor(apiKey))
                .build();

        asyncStub = GddsStreamServiceGrpc.newStub(channel);
        blockingStub = GddsStreamServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Interceptor for attaching API key to outgoing gRPC requests.
     */
    private static class ApiKeyClientInterceptor implements ClientInterceptor {
        private final String apiKey;
        private static final Metadata.Key<String> API_KEY_HEADER = 
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

        public ApiKeyClientInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                io.grpc.MethodDescriptor<ReqT, RespT> method,
                io.grpc.CallOptions callOptions,
                io.grpc.Channel next) {
            return new io.grpc.ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata metadata) {
                    metadata.put(API_KEY_HEADER, apiKey);
                    super.start(responseListener, metadata);
                }
            };
        }
    }

    /**
     * Requests a unique client ID from the server.
     *
     * @return The client ID.
     * @throws RuntimeException if the request fails.
     */
    public String getClientId() {
        try {
            GenerateClientIdRequest request = GenerateClientIdRequest.newBuilder().build();
            GenerateClientIdResponse response = blockingStub.generateClientId(request);
            clientId = response.getClientId();
            return clientId;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
                throw new RuntimeException("Authentication failed: " + e.getStatus().getDescription(), e);
            }
            throw new RuntimeException("Failed to generate client ID: " + e.getStatus().getDescription(), e);
        }
    }

    /**
     * Subscribes to the GDDS stream and processes incoming messages.
     */
    public void startReceiving() {
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException("Client ID not set. Call getClientId first.");
        }

        SubscriptionRequest request = SubscriptionRequest.newBuilder()
                .setClientId(clientId)
                .build();

        asyncStub.subscribe(request, new StreamObserver<GddsMessage>() {
            @Override
            public void onNext(GddsMessage gddsMessage) {
                System.out.printf("Received GDDS: IP=%s, VehicleNumber=%s, Lat=%.6f, Lon=%.6f, Speed=%.2f, " +
                                "Bearing=%.2f, TransitMode=%s, Timestamp=%s, ServerTimestamp=%s, Message=%s%n",
                        gddsMessage.getIp(),
                        gddsMessage.getVeh(),
                        gddsMessage.getLat(),
                        gddsMessage.getLon(),
                        gddsMessage.getSpd(),
                        gddsMessage.getHdg(),
                        VehicleType.valueOf(gddsMessage.getTrnTyp()),
                        OffsetDateTime.ofInstant(Instant.ofEpochMilli(gddsMessage.getTm()), java.time.ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(Instant.ofEpochMilli(gddsMessage.getSvrtm()), java.time.ZoneOffset.UTC),
                        gddsMessage.getMsg());
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof StatusRuntimeException) {
                    Status status = ((StatusRuntimeException) t).getStatus();
                    if (status.getCode() == Status.Code.CANCELLED) {
                        System.out.println("Client " + clientId + " subscription was cancelled.");
                    } else if (status.getCode() == Status.Code.UNAUTHENTICATED) {
                        System.out.println("Client " + clientId + " failed to subscribe: " + status.getDescription());
                    } else if (status.getCode() == Status.Code.ALREADY_EXISTS) {
                        System.out.println("Client " + clientId + " failed to subscribe: " + status.getDescription());
                    } else {
                        System.out.println("Error in subscription for client " + clientId + ": " + status.getDescription());
                    }
                } else {
                    System.out.println("Error in subscription for client " + clientId + ": " + t.getMessage());
                }
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Client " + clientId + " stream completed.");
                latch.countDown();
            }
        });

        System.out.println("Client " + clientId + " subscribed to GDDS stream.");
    }

    /**
     * Terminates the subscription.
     */
    public void terminate() {
        if (clientId == null || clientId.isEmpty()) {
            System.out.println("No client ID set. Nothing to terminate.");
            return;
        }

        try {
            TerminationRequest request = TerminationRequest.newBuilder()
                    .setClientId(clientId)
                    .build();
            TerminationResponse response = blockingStub.terminate(request);

            System.out.println(response.getSuccess()
                    ? "Client " + clientId + " successfully terminated subscription."
                    : "Client " + clientId + " termination failed.");
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
                System.out.println("Client " + clientId + " failed to terminate: " + e.getStatus().getDescription());
            } else {
                System.out.println("Error terminating client " + clientId + ": " + e.getStatus().getDescription());
            }
        } finally {
            latch.countDown();
        }
    }

    /**
     * Waits for the subscription to complete or terminate.
     */
    public void awaitTermination() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Await termination interrupted: " + e.getMessage());
        }
    }

    /**
     * Example usage of the GddsClient.
     */
    public static void main(String[] args) {
        // Replace {SERVER_ADDRESS} with the actual server address (e.g., localhost:50051)
        String serverAddress = "{SERVER_ADDRESS}";
        String apiKey = "valid-api-key-123"; // Replace with actual API key

        try {
            GddsClient client = new GddsClient(serverAddress, apiKey);

            String clientId = client.getClientId();
            System.out.println("Client ID: " + clientId);

            new Thread(client::startReceiving).start();

            System.out.println("Press Enter to terminate...");
            new Scanner(System.in).nextLine();

            client.terminate();
            client.awaitTermination();
        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        }

        System.out.println("Client shut down.");
    }
}