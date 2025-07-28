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
            string serverAddress = "{SERVER_ADDRESS}"; // Replace with actual address
            string apiKey = "valid-api-key-123"; // Replace with your API key
            string tokenFile = "connection_token.txt";
            string connectionToken;

            // Load or generate connection token
            if (File.Exists(tokenFile))
            {
                connectionToken = File.ReadAllText(tokenFile).Trim();
                Console.WriteLine($"Using saved connection token: {connectionToken}");
            }
            else
            {
                connectionToken = Guid.NewGuid().ToString();
                File.WriteAllText(tokenFile, connectionToken);
                Console.WriteLine($"Created new connection token: {connectionToken}");
            }

            try
            {
                using var channel = GrpcChannel.ForAddress($"https://{serverAddress}");
                var client = new GddsStreaming.GddsStreamService.GddsStreamServiceClient(channel);
                var metadata = new Metadata { { "x-api-key", apiKey } };
                var request = new SubscriptionRequest { ConnectionToken = connectionToken };

                using var call = client.Subscribe(request, metadata);
                Console.WriteLine($"Connected with token: {connectionToken}");
                await foreach (var message in call.ResponseStream.ReadAllAsync())
                {
                    Console.WriteLine($"Vehicle: {message.Veh}, Location: ({message.Lat}, {message.Lon}), Time: {message.Tm}, Server Time: {message.SvrTm}");
                }
            }
            catch (RpcException ex) when (ex.StatusCode == StatusCode.AlreadyExists)
            {
                Console.WriteLine($"Duplicate token {connectionToken}. Please generate a new one.");
            }
            catch (RpcException ex)
            {
                Console.WriteLine($"Error: {ex.Status.Detail}");
            }
        }
    }
}
