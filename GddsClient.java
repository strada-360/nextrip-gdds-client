package com.gddsstreaming.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CountDownLatch;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Scanner;

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

    public GddsClient(String serverAddress) {
        if (serverAddress == null || serverAddress.isEmpty()) {
            throw new IllegalArgumentException("Server address is required");
        }

        // Create gRPC channel
        ManagedChannel channel = ManagedChannelBuilder.forTarget(serverAddress)
                .usePlaintext() // Use plaintext for simplicity; configure SSL for production
                .build();

        // Create stubs for async and blocking calls
        asyncStub = GddsStreamServiceGrpc.newStub(channel);
        blockingStub = GddsStreamServiceGrpc.newBlockingStub(channel);
    }

    public String getClientId() {
        try {
            GenerateClientIdRequest request = GenerateClientIdRequest.newBuilder().build();
            GenerateClientIdResponse response = blockingStub.generateClientId(request);
            clientId = response.getClientId();
            return clientId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate client ID: " + e.getMessage(), e);
        }
    }

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
                System.out.printf("Received GDDS: IP=%s, Vehicle=%s, Lat=%f, Lon=%f, Speed=%f, " +
                                "Bearing=%f, TransitMode=%s, Timestamp=%s, ServerTimestamp=%s, Message=%s%n",
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
                if (t instanceof io.grpc.StatusRuntimeException) {
                    io.grpc.Status status = ((io.grpc.StatusRuntimeException) t).getStatus();
                    if (status.getCode() == Status.Code.CANCELLED) {
                        System.out.println("Client " + clientId + " subscription was cancelled.");
                    } else if (status.getCode() == Status.Code.UNAUTHENTICATED) {
                        System.out.println("Client " + clientId + " failed to subscribe: Invalid or unauthorized client ID.");
                    } else {
                        System.out.println("Error in subscription for client " + clientId + ": " + t.getMessage());
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

            if (response.getSuccess()) {
                System.out.println("Client " + clientId + " successfully terminated subscription.");
            } else {
                System.out.println("Client " + clientId + " termination failed.");
            }
        } catch (Exception e) {
            System.out.println("Error terminating client " + clientId + ": " + e.getMessage());
        } finally {
            latch.countDown();
        }
    }

    public void awaitTermination() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Await termination interrupted: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String serverAddress = "localhost:50051"; // Replace with your server address

        GddsClient client = new GddsClient(serverAddress);

        // Request a client ID
        String clientId = client.getClientId();
        System.out.println("Received client ID: " + clientId);

        // Start receiving in a separate thread
        new Thread(client::startReceiving).start();

        // Wait for user input to terminate
        System.out.println("Press Enter to terminate the subscription...");
        new Scanner(System.in).nextLine();

        // Terminate the subscription
        client.terminate();

        // Wait for the stream to complete
        client.awaitTermination();

        System.out.println("Client shut down.");
    }
}