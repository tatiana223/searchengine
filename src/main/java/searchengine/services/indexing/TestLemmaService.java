package searchengine.services.indexing;

import java.io.IOException;
import java.util.HashMap;

public class TestLemmaService {
    public static void main(String[] args) throws IOException {
        LemmaService service = new LemmaService();

        String testText = "Красивые кошки vhg бегут по зеленому полю";
        HashMap<String, Integer> result = service.getLemmas(testText);

        System.out.println("Найденные леммы");
        for (var entry : result.entrySet()) {
            System.out.println(entry.getKey() + " - " + entry.getValue());
        }
    }
}
