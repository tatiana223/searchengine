package searchengine.services.indexing;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class LemmaService {

    private final LuceneMorphology luceneMorph;


    public LemmaService() throws IOException {
        this.luceneMorph = new RussianLuceneMorphology();
    }

    public String cleanHtml(String html) {
        return Jsoup.parse(html).text();
    }

    private boolean isServiceWord(List<String> morphInfo) {
        for (String info : morphInfo) {
            String props = info.toUpperCase();


            if (Stream.of("МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ", "МС", "ВВОДН").anyMatch(props::contains)) {
                return true;
            }
        }
        return false;
    }

    public HashMap<String, Integer> getLemmas(String text) {
        HashMap<String, Integer> lemmas = new HashMap<>();

        String cleanedText = cleanHtml(text);

        String[] words = cleanedText.toLowerCase(Locale.ROOT).split("\\s+");
        for (String word : words) {
            if (word.isEmpty() || !word.matches("[а-яё]+")) {
                continue;
            }

            try {
                List<String> normalForms = luceneMorph.getNormalForms(word);
                if (normalForms.isEmpty()) {
                    continue;
                }

                String lemma = normalForms.get(0);

                List<String> morphInfo = luceneMorph.getMorphInfo(word);
                if (isServiceWord(morphInfo)) {
                    continue;
                }
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            } catch (Exception e) {
                continue;
            }
        }
        return lemmas;
    }
}
