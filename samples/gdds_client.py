import grpc
import threading
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor
from proto.gdds_streaming_pb2 import (
    GenerateClientIdRequest,
    SubscriptionRequest,
    TerminationRequest,
    GddsStreamServiceStub
)
from proto.gdds_streaming_pb2_grpc import GddsStreamServiceStub

class VehicleType:
    UNKNOWN = 0
    CAR = 1
    TRUCK = 2
    BUS = 3
    MOTORCYCLE = 4

    @staticmethod
    def to_string(value):
        return {
            0: "UNKNOWN",
            1: "CAR",
            2: "TRUCK",
            3: "BUS",
            4: "MOTORCYCLE"
        }.get(value, "UNKNOWN")

class GddsClient:
    def __init__(self, server_address):
        if not server_address:
            raise ValueError("Server address is required")
        
        self.server_address = server_address
        self.client_id = None
        self._channel = grpc.insecure_channel(server_address)  # Use insecure_channel for simplicity; configure SSL for production
        self._stub = GddsStreamServiceStub(self._channel)
        self._stop_event = threading.Event()

    def get_client_id(self):
        try:
            response = self._stub.GenerateClientId(GenerateClientIdRequest())
            self.client_id = response.clientId
            return self.client_id
        except grpc.RpcError as e:
            raise RuntimeError(f"Failed to generate client ID: {e}")

    def start_receiving(self):
|        if not self.client_id:
            raise RuntimeError("Client ID not set. Call get_client_id first.")

        try:
            request = SubscriptionRequest(clientId=self.client_id)
            response_iterator = self._stub.Subscribe(request)

            print(f"Client {self.client_id} subscribed to GDDS stream.")

            for gdds_message in response_iterator:
                if self._stop_event.is_set():
                    break

                timestamp = datetime.fromtimestamp(gdds_message.tm / 1000.0, tz=timezone.utc)
                server_timestamp = datetime.fromtimestamp(gdds_message.svrtm / 1000.0, tz=timezone.utc)

                print(f"Received GDDS: IP={gdds_message.ip}, Vehicle={gdds_message.veh}, "
                      f"Lat={gdds_message.lat}, Lon={gdds_message.lon}, Speed={gdds_message.spd}, "
                      f"Bearing={gdds_message.hdg}, TransitMode={VehicleType.to_string(gdds_message.trnTyp)}, "
                      f"Timestamp={timestamp}, ServerTimestamp={server_timestamp}, Message={gdds_message.msg}")

        except grpc.RpcError as e:
            status_code = e.code()
            if status_code == grpc.StatusCode.CANCELLED:
                print(f"Client {self.client_id} subscription was cancelled.")
            elif status_code == grpc.StatusCode.UNAUTHENTICATED:
                print(f"Client {self.client_id} failed to subscribe: Invalid or unauthorized client ID.")
            else:
                print(f"Error in subscription for client {self.client_id}: {e}")
            self._stop_event.set()

    def terminate(self):
        if not self.client_id:
            print("No client ID set. Nothing to terminate.")
            return

        try:
            request = TerminationRequest(clientId=self.client_id)
            response = self._stub.Terminate(request)

            if response.success:
                print(f"Client {self.client_id} successfully terminated subscription.")
            else:
                print(f"Client {self.client_id} termination failed.")

            self._stop_event.set()
        except grpc.RpcError as e:
            print(f"Error terminating client {self.client_id}: {e}")
            self._stop_event.set()

    def close(self):
        self._stop_event.set()
        self._channel.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

def main():
    server_address = "localhost:50051"  # Replace with your server address

    with GddsClient(server_address) as client:
        # Request a client ID
        client_id = client.get_client_id()
        print(f"Received client ID: {client_id}")

        # Start receiving in a separate thread
        with ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(client.start_receiving)

            # Wait for user input to terminate
            print("Press Enter to terminate the subscription...")
            input()

            # Terminate the subscription
            client.terminate()

            # Wait for the receiving thread to complete
            future.result()

        print("Client shut down.")

if __name__ == "__main__":
    main()