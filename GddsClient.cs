using Grpc.Core;
using Grpc.Net.Client;
using System;
using System.Threading;
using System.Threading.Tasks;

namespace GddsStreaming.Client
{
    public class GddsClient
    {
        private readonly GddsStreamService.GddsStreamServiceClient _client;
        private readonly string _serverAddress;
        private string? _clientId;
        private readonly CancellationTokenSource _cancellationTokenSource;

        public GddsClient(string serverAddress)
        {
            if (string.IsNullOrEmpty(serverAddress))
                throw new ArgumentException("Server address is required", nameof(serverAddress));

            _serverAddress = serverAddress;
            _cancellationTokenSource = new CancellationTokenSource();

            // Create gRPC channel and client
            var channel = GrpcChannel.ForAddress(serverAddress, new GrpcChannelOptions
            {
                // Optional: Configure channel options (e.g., SSL, credentials)
            });
            _client = new GddsStreamService.GddsStreamServiceClient(channel);
        }

        public async Task<string> GetClientIdAsync()
        {
            try
            {
                var response = await _client.GenerateClientIdAsync(new GenerateClientIdRequest());
                _clientId = response.ClientId;
                return _clientId;
            }
            catch (Exception ex)
            {
                throw new Exception($"Failed to generate client ID: {ex.Message}", ex);
            }
        }

        public async Task StartReceivingAsync()
        {
            if (string.IsNullOrEmpty(_clientId))
            {
                throw new InvalidOperationException("Client ID not set. Call GetClientIdAsync first.");
            }

            try
            {
                // Subscribe to the GDDS stream
                var subscriptionRequest = new SubscriptionRequest { ClientId = _clientId };
                using var streamingCall = _client.Subscribe(subscriptionRequest, cancellationToken: _cancellationTokenSource.Token);

                Console.WriteLine($"Client {_clientId} subscribed to GDDS stream.");

                // Asynchronously read messages from the stream
                await foreach (var gddsMessage in streamingCall.ResponseStream.ReadAllAsync(_cancellationTokenSource.Token))
                {
                    Console.WriteLine($"Received GDDS: IP={gddsMessage.Ip}, Vehicle={gddsMessage.Veh}, " +
                        $"Lat={gddsMessage.Lat}, Lon={gddsMessage.Lon}, Speed={gddsMessage.Spd}, " +
                        $"Bearing={gddsMessage.Hdg}, TransitMode={(VehicleType)gddsMessage.TrnTyp}, " +
                        $"Timestamp={DateTimeFromUnixMs(gddsMessage.Tm)}, " +
                        $"ServerTimestamp={DateTimeFromUnixMs(gddsMessage.Svrtm)}, Message={gddsMessage.Msg}");
                }
            }
            catch (RpcException ex) when (ex.StatusCode == StatusCode.Cancelled)
            {
                Console.WriteLine($"Client {_clientId} subscription was cancelled.");
            }
            catch (RpcException ex) when (ex.StatusCode == StatusCode.Unauthenticated)
            {
                Console.WriteLine($"Client {_clientId} failed to subscribe: Invalid or unauthorized client ID.");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error in subscription for client {_clientId}: {ex.Message}");
            }
        }

        public async Task TerminateAsync()
        {
            if (string.IsNullOrEmpty(_clientId))
            {
                Console.WriteLine("No client ID set. Nothing to terminate.");
                return;
            }

            try
            {
                // Send termination request
                var terminationRequest = new TerminationRequest { ClientId = _clientId };
                var response = await _client.TerminateAsync(terminationRequest);

                if (response.Success)
                {
                    Console.WriteLine($"Client {_clientId} successfully terminated subscription.");
                }
                else
                {
                    Console.WriteLine($"Client {_clientId} termination failed.");
                }

                // Cancel the streaming operation
                _cancellationTokenSource.Cancel();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error terminating client {_clientId}: {ex.Message}");
            }
        }

        private static DateTime DateTimeFromUnixMs(long unixMs)
        {
            return DateTimeOffset.FromUnixTimeMilliseconds(unixMs).UtcDateTime;
        }

        public void Dispose()
        {
            _cancellationTokenSource.Cancel();
            _cancellationTokenSource.Dispose();
        }
    }

    public enum VehicleType
    {
        Unknown = 0,
        Car = 1,
        Truck = 2,
        Bus = 3,
        Motorcycle = 4
    }

    // Example usage
    public class Program
    {
        public static async Task Main()
        {
            string serverAddress = "https://localhost:5001"; // Replace with your server address

            using var client = new GddsClient(serverAddress);

            // Request a client ID from the server
            string clientId = await client.GetClientIdAsync();
            Console.WriteLine($"Received client ID: {clientId}");

            // Start receiving in a background task
            var receiveTask = client.StartReceivingAsync();

            // Simulate some work or wait for user input
            Console.WriteLine("Press any key to terminate the subscription...");
            Console.ReadKey();

            // Terminate the subscription
            await client.TerminateAsync();

            // Wait for the receiving task to complete
            await receiveTask;

            Console.WriteLine("Client shut down.");
        }
    }
}