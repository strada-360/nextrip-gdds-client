using Grpc.Core;
using Grpc.Core.Interceptors;
using Grpc.Net.Client;
using System;
using System.Threading;
using System.Threading.Tasks;

namespace GddsStreaming.Client
{
    /// <summary>
    /// Interceptor for attaching API key to outgoing gRPC requests.
    /// </summary>
    public class ApiKeyClientInterceptor : Interceptor
    {
        private readonly string _apiKey;

        public ApiKeyClientInterceptor(string apiKey)
        {
            _apiKey = apiKey ?? throw new ArgumentNullException(nameof(apiKey));
        }

        public override AsyncUnaryCall<TResponse> AsyncUnaryCall<TRequest, TResponse>(
            TRequest request,
            ClientInterceptorContext<TRequest, TResponse> context,
            AsyncUnaryCallContinuation<TRequest, TResponse> continuation)
        {
            var metadata = context.Options.Metadata ?? new Metadata();
            metadata.Add("x-api-key", _apiKey);
            var newOptions = context.Options.WithMetadata(metadata);
            var newContext = new ClientInterceptorContext<TRequest, TResponse>(context.Method, context.Host, newOptions);
            return continuation(request, newContext);
        }

        public override AsyncServerStreamingCall<TResponse> AsyncServerStreamingCall<TRequest, TResponse>(
            TRequest request,
            ClientInterceptorContext<TRequest, TResponse> context,
            AsyncServerStreamingCallContinuation<TRequest, TResponse> continuation)
        {
            var metadata = context.Options.Metadata ?? new Metadata();
            metadata.Add("x-api-key", _apiKey);
            var newOptions = context.Options.WithMetadata(metadata);
            var newContext = new ClientInterceptorContext<TRequest, TResponse>(context.Method, context.Host, newOptions);
            return continuation(request, newContext);
        }
    }

    /// <summary>
    /// Client for interacting with the GddsStreamService to receive GPS Data Distribution System (GDDS) records.
    /// </summary>
    public class GddsClient : IDisposable
    {
        private readonly GddsStreamService.GddsStreamServiceClient _client;
        private readonly string _serverAddress;
        private string? _clientId;
        private readonly CancellationTokenSource _cancellationTokenSource;

        public GddsClient(string serverAddress, string apiKey)
        {
            if (string.IsNullOrEmpty(serverAddress))
                throw new ArgumentException("Server address cannot be null or empty", nameof(serverAddress));
            if (string.IsNullOrEmpty(apiKey))
                throw new ArgumentException("API key cannot be null or empty", nameof(apiKey));

            _serverAddress = serverAddress;
            _cancellationTokenSource = new CancellationTokenSource();

            var channel = GrpcChannel.ForAddress(serverAddress, new GrpcChannelOptions
            {
                Interceptors = { new ApiKeyClientInterceptor(apiKey) }
            });
            _client = new GddsStreamService.GddsStreamServiceClient(channel);
        }

        /// <summary>
        /// Requests a unique client ID from the server.
        /// </summary>
        public async Task<string> GetClientIdAsync()
        {
            try
            {
                var response = await _client.GenerateClientIdAsync(new GenerateClientIdRequest());
                _clientId = response.ClientId;
                return _clientId;
            }
            catch (RpcException ex) when (ex.StatusCode == StatusCode.Unauthenticated)
            {
                throw new Exception($"Authentication failed: {ex.Status.Detail}", ex);
            }
            catch (RpcException ex)
            {
                throw new Exception($"Failed to generate client ID: {ex.Status.Detail}", ex);
            }
        }

        /// <summary>
        /// Subscribes to the GDDS stream and processes incoming messages.
        /// </summary>
        public async Task StartReceivingAsync()
        {
            if (string.IsNullOrEmpty(_clientId))
            {
                throw new InvalidOperationException("Client ID not set. Call GetClientIdAsync first.");
            }

            try
            {
                var subscriptionRequest = new SubscriptionRequest { ClientId = _clientId };
                using var streamingCall = _client.Subscribe(subscriptionRequest, cancellationToken: _cancellationTokenSource.Token);

                Console.WriteLine($"Client {_clientId} subscribed to GDDS stream.");

                await foreach (var gddsMessage in streamingCall.ResponseStream.ReadAllAsync(_cancellationTokenSource.Token))
                {
                    Console.WriteLine($"Received GDDS: IP={gddsMessage.Ip}, VehicleNumber={gddsMessage.Veh}, " +
                        $"Lat={gddsMessage.Lat:F6}, Lon={gddsMessage.Lon:F6}, Speed={gddsMessage.Spd:F2}, " +
                        $"Bearing={gddsMessage.Hdg:F2}, TransitMode={(VehicleType)gddsMessage.TrnTyp}, " +
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
                Console.WriteLine($"Client {_clientId} failed to subscribe: {ex.Status.Detail}");
            }
            catch (RpcException ex) when (ex.StatusCode == StatusCode.AlreadyExists)
            {
                Console.WriteLine($"Client {_clientId} failed to subscribe: {ex.Status.Detail}");
            }
            catch (RpcException ex)
            {
                Console.WriteLine($"Error in subscription for client {_clientId}: {ex.Status.Detail}");
            }
        }

        /// <summary>
        /// Terminates the subscription and cleans up resources.
        /// </summary>
        public async Task TerminateAsync()
        {
            if (string.IsNullOrEmpty(_clientId))
            {
                Console.WriteLine("No client ID set. Nothing to terminate.");
                return;
            }

            try
            {
                var terminationRequest = new TerminationRequest { ClientId = _clientId };
                var response = await _client.TerminateAsync(terminationRequest);

                Console.WriteLine(response.Success
                    ? $"Client {_clientId} successfully terminated subscription."
                    : $"Client {_clientId} termination failed.");

                _cancellationTokenSource.Cancel();
            }
            catch (RpcException ex) when (ex.StatusCode == StatusCode.Unauthenticated)
            {
                Console.WriteLine($"Client {_clientId} failed to terminate: {ex.Status.Detail}");
            }
            catch (RpcException ex)
            {
                Console.WriteLine($"Error terminating client {_clientId}: {ex.Status.Detail}");
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

    /// <summary>
    /// Example usage of the GddsClient.
    /// </summary>
    public class Program
    {
        public static async Task Main()
        {
            // Replace {SERVER_ADDRESS} with the actual server address (e.g., https://localhost:5001)
            string serverAddress = "{SERVER_ADDRESS}";
            string apiKey = "valid-api-key-123"; // Replace with actual API key

            using var client = new GddsClient(serverAddress, apiKey);

            try
            {
                string clientId = await client.GetClientIdAsync();
                Console.WriteLine($"Received client ID: {clientId}");

                var receiveTask = client.StartReceivingAsync();

                Console.WriteLine("Press Enter to terminate the subscription...");
                Console.ReadLine();

                await client.TerminateAsync();
                await receiveTask;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Client error: {ex.Message}");
            }

            Console.WriteLine("Client shut down.");
        }
    }
}