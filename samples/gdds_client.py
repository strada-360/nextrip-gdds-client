import uuid
import grpc
from gdds_streaming_pb2 import SubscriptionRequest
from gdds_streaming_pb2_grpc import GddsStreamServiceStub

def main():
    server_address = "{SERVER_ADDRESS}" # Replace with actual address
    api_key = "valid-api-key-123" # Replace with your API key
    token_file = "connection_token.txt"
    connection_token = None

    # Load or generate connection token
    try:
        with open(token_file, "r") as f:
            connection_token = f.read().strip()
            print(f"Using saved connection token: {connection_token}")
    except FileNotFoundError:
        connection_token = str(uuid.uuid4())
        with open(token_file, "w") as f:
            f.write(connection_token)
        print(f"Created new connection token: {connection_token}")

    try:
        with grpc.secure_channel(server_address, grpc.ssl_channel_credentials()) as channel:
            stub = GddsStreamServiceStub(channel)
            metadata = [("x-api-key", api_key)]
            request = SubscriptionRequest(connectionToken=connection_token)
            print(f"Connected with token: {connection_token}")
            for message in stub.Subscribe(request, metadata=metadata):
                print(f"Vehicle: {message.veh}, Location: ({message.lat}, {message.lon}), Time: {message.tm}, Server Time: {message.svrTm}")
    except grpc.RpcError as e:
        if e.code() == grpc.StatusCode.ALREADY_EXISTS:
            print(f"Duplicate token {connection_token}. Please generate a new one.")
        else:
            print(f"Error: {e.details()}")

if __name__ == "__main__":
    main()
