import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;

public class GddsClient {
    public static void main(String[] args) {
        String serverAddress = "{SERVER_ADDRESS}"; // Replace with actual address
        String apiKey = "valid-api-key-123"; // Replace with your API key
        String tokenFile = "connection_token.txt";
        String connectionToken;

        // Load or generate connection token
        try (FileReader reader = new FileReader(tokenFile)) {
            char[] buffer = new char[36];
            reader.read(buffer);
            connectionToken = new String(buffer).trim();
            System.out.println("Using saved connection token: " + connectionToken);
        } catch (Exception e) {
            connectionToken = UUID.randomUUID().toString();
            try (FileWriter writer = new FileWriter(tokenFile)) {
                writer.write(connectionToken);
            }
            System.out.println("Created new connection token: " + connectionToken);
        }

        ManagedChannel channel = null;
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
            System.out.println("Connected with token: " + connectionToken);
            while (messages.hasNext()) {
                GddsMessage message = messages.next();
                System.out.println("Vehicle: " + message.getVeh() + 
                    ", Location: (" + message.getLat() + ", " + message.getLon() + 
                    "), Time: " + message.getTm() + ", Server Time: " + message.getSvrTm());
            }
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.ALREADY_EXISTS) {
                System.out.println("Duplicate token " + connectionToken + ". Please generate a new one.");
            } else {
                System.out.println("Error: " + e.getStatus().getDescription());
            }
        } finally {
            if (channel != null) {
                channel.shutdown();
            }
        }
    }
}
