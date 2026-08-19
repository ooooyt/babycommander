package com.ooooyt.babycommander.tool;

import com.ooooyt.babycommander.hook.HookManager;
import com.ooooyt.babycommander.util.I18n;
import com.ooooyt.babycommander.util.MessageKey;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class InternetTool extends BaseTool {

    private static final String SEARCH_URL = "https://html.duckduckgo.com/html/?q=";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private int maxOutputLength = 20 * 1024;

    public void setMaxOutputLength(int bytes) {
        this.maxOutputLength = bytes;
    }

    public InternetTool(HookManager hookManager) {
        super(hookManager);
    }

    @Tool("Fetch URL content as plain text")
    public String fetchUrl(String url) {
        return executeWithStatus("InternetTool", "fetch_url", url, () -> doFetchUrl(url));
    }

    private String doFetchUrl(String url) {
        try {
            URI uri = URI.create(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; CodeGen/2.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return I18n.tr(MessageKey.NET_HTTP_ERROR, response.statusCode(), url);
            }

            Document doc = Jsoup.parse(response.body());
            String text = doc.body().text();
            if (text == null || text.isBlank()) {
                return I18n.tr(MessageKey.NET_FETCH_EMPTY, response.statusCode());
            }
            if (text.length() > maxOutputLength) {
                text = truncateSafely(text, maxOutputLength)
                    + "\n... [truncated at " + (maxOutputLength / 1024) + "KB] ...\n";
            }
            return text;
        } catch (Exception e) {
            return I18n.tr(MessageKey.NET_FETCH_ERROR, url, e.getMessage());
        }
    }

    @Tool("Search DuckDuckGo, returns titles, snippets, and URLs")
    public String search(String query) {
        return executeWithStatus("InternetTool", "internet_search", query, () -> doSearch(query));
    }

    private String doSearch(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = SEARCH_URL + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; CodeGen/2.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return I18n.tr(MessageKey.NET_SEARCH_HTTP_ERROR, response.statusCode());
            }

            Document doc = Jsoup.parse(response.body());
            StringBuilder results = new StringBuilder(I18n.tr(MessageKey.NET_SEARCH_HEADER, query) + "\n\n");

            Elements resultsElements = doc.select("div.results_links_deep");
            if (resultsElements.isEmpty()) {
                resultsElements = doc.select("a.result__a");
            }

            int count = 0;
            for (Element result : resultsElements) {
                if (count >= 10) break;

                String title = result.text().trim();
                String href = result.attr("href");
                if (href.startsWith("/")) {
                    href = "https://html.duckduckgo.com" + href;
                }

                if (!title.isEmpty()) {
                    results.append(count + 1).append(". ").append(title).append("\n");
                    results.append("   URL: ").append(href).append("\n\n");
                    count++;
                }
            }

            if (count == 0) {
                Elements snippets = doc.select(".result__snippet");
                Elements links = doc.select(".result__a");
                for (int i = 0; i < Math.min(links.size(), 10); i++) {
                    results.append((i + 1)).append(". ").append(links.get(i).text()).append("\n");
                    if (i < snippets.size()) {
                        results.append("   ").append(snippets.get(i).text()).append("\n");
                    }
                    results.append("   URL: ").append(links.get(i).attr("href")).append("\n\n");
                }
                count = Math.min(links.size(), 10);
            }

            if (count == 0) {
                return I18n.tr(MessageKey.NET_SEARCH_NO_RESULTS, query);
            }

            return results.toString().trim();
        } catch (Exception e) {
            return I18n.tr(MessageKey.NET_SEARCH_ERROR, e.getMessage());
        }
    }
}
