package searchengine.services.indexing;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class LemmaService {

    private final LuceneMorphology russianMorph;
    private final LuceneMorphology englishMorph;


    public LemmaService() throws IOException {
        this.russianMorph = new RussianLuceneMorphology();
        this.englishMorph = new EnglishLuceneMorphology();
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
            if (Stream.of("CONJ", "PREP", "ARTICLE", "PART", "PRON").anyMatch(props::contains)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRussianWord(String word) {
        return word.matches("[а-яё]+");
    }

    private boolean isEnglishWord(String word) {
        return word.matches("[a-z]+");
    }

    public HashMap<String, Integer> getLemmas(String text) {
        HashMap<String, Integer> lemmas = new HashMap<>();

        String cleanedText = cleanHtml(text);

        String[] words = cleanedText.toLowerCase(Locale.ROOT).split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            try {
                List<String> normalForms;
                List<String> morphInfo;
                if (isRussianWord(word)) {
                    normalForms = russianMorph.getNormalForms(word);
                    morphInfo = russianMorph.getMorphInfo(word);
                } else if (isEnglishWord(word)) {
                    normalForms = englishMorph.getNormalForms(word);
                    morphInfo = englishMorph.getMorphInfo(word);
                } else {
                    continue;
                }

                if (normalForms.isEmpty()) {
                    continue;
                }

                String lemma = normalForms.get(0);

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
