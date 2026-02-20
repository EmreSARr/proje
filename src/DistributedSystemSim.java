import java.util.Arrays;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

// HATA ALMAMAK İÇİN HER ŞEYİ TEK BİR 'CLASS' İÇİNE ALDIM
public class DistributedSystemSim {

    // --- VERİ MODELİ (Record) ---
    // Statik yapıyoruz ki main içinden erişebilelim
    public record RequestResult(int serverId, double latency) {}

    // --- 1. ENVIRONMENT (ORTAM) ---
    static class Server {
        private final int id;
        private double currentMeanLatency;
        private final RandomGenerator rng = RandomGenerator.getDefault();

        public Server(int id, double startLatency) {
            this.id = id;
            this.currentMeanLatency = startLatency;
        }

        public RequestResult handleRequest() {
            double noise = rng.nextGaussian() * 5;
            double actualLatency = Math.max(10, currentMeanLatency + noise);
            currentMeanLatency += rng.nextGaussian() * 2;
            return new RequestResult(id, actualLatency);
        }
    }

    // --- 2. AGENT (SOFTMAX) ---
    static class SoftmaxAgent {
        private final double[] qValues;
        private final double temperature;
        private final double learningRate;
        private final RandomGenerator rng = RandomGenerator.getDefault();

        public SoftmaxAgent(int k, double temperature, double learningRate) {
            this.qValues = new double[k];
            this.temperature = temperature;
            this.learningRate = learningRate;
            Arrays.fill(qValues, 0.0);
        }

        public int selectServer() {
            // Nümerik Stabilite (Overflow engelleme)
            double maxQ = Arrays.stream(qValues).max().orElse(0.0);

            double[] probabilities = Arrays.stream(qValues)
                    .map(q -> Math.exp((q - maxQ) / temperature))
                    .toArray();

            double sumExp = Arrays.stream(probabilities).sum();
            double r = rng.nextDouble() * sumExp;

            double cumulative = 0.0;
            for (int i = 0; i < probabilities.length; i++) {
                cumulative += probabilities[i];
                if (r <= cumulative) return i;
            }
            return probabilities.length - 1;
        }

        public void update(int serverIndex, double latency) {
            double reward = -latency;
            qValues[serverIndex] += learningRate * (reward - qValues[serverIndex]);
        }

        public void printStats() {
            System.out.print("   [Tahmini Latencyler]: ");
            for (int i = 0; i < qValues.length; i++) {
                System.out.printf("S%d: %.1fms | ", i, -qValues[i]);
            }
            System.out.println();
        }
    }

    // --- 3. MAIN METODU (KLASİK YAPI) ---
    // Burası artık standart "public static void main" olduğu için hata vermez.
    public static void main(String[] args) {
        var serverCount = 5;
        var totalRequests = 2000;

        var cluster = IntStream.range(0, serverCount)
                .mapToObj(i -> new Server(i, 60 + i * 20))
                .toArray(Server[]::new);

        var agent = new SoftmaxAgent(serverCount, 15.0, 0.1);

        System.out.println("=== Softmax Load Balancer Simülasyonu ===");

        double totalLatency = 0;

        for (int t = 0; t < totalRequests; t++) {
            int serverId = agent.selectServer();

            // Record kullanımı
            RequestResult result = cluster[serverId].handleRequest();

            agent.update(result.serverId(), result.latency());

            totalLatency += result.latency();

            if ((t + 1) % 200 == 0) {
                double avgLatency = totalLatency / (t + 1);

                System.out.printf("""
                        
                        --- Rapor (İstek #%d) ---
                        Seçilen Sunucu: S%d
                        Anlık Latency : %.1f ms
                        Genel Ortalama: %.1f ms
                        """, (t + 1), serverId, result.latency(), avgLatency);

                agent.printStats();
            }
        }
        System.out.println("\n=== Simülasyon Bitti ===");
    }
}