package de.gn0m3r4l.garminliker.customActions;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import de.gn0m3r4l.garminliker.actions.FitnessCheckActions;
import de.gn0m3r4l.garminliker.actions.FitnessCheckerActionsImpl;
import de.gn0m3r4l.garminliker.utils.TimeConstants;
import de.gn0m3r4l.garminliker.utils.Utils;
import static de.gn0m3r4l.garminliker.utils.Utils.pretendToBeHuman;

public class KomootChecker extends FitnessCheckerActionsImpl {
    public static final int RGB_MIN_GREY = 35;
    private static final int MAX_LIKES_PER_RUN = 100;
    private static KomootChecker checker;

    /**
     * Constructor initializes the provider name for logging purposes.
     */
    public KomootChecker() {
        this.providerName = "Komoot";
        this.maxLikesPerRun = MAX_LIKES_PER_RUN;
    }

    /**
     * Singleton accessor for KomootChecker instance with lazy initialization.
     *
     * @return Singleton instance of KomootChecker
     */
    public static FitnessCheckActions getInstance() {
        if (checker == null) {
            checker = new KomootChecker();
        }
        return checker;
    }

    @Override
    protected void runUrl(Page page, String url) {
        page.navigate(url);
        pretendToBeHuman(TimeConstants.DELAY_AFTER_PAGE_LOAD);

        var element = waitForMainPageById(page, "pageMountNode");

        List<Locator> articles = collectArticles(page, element);

        String[] blacklist = new String[]{"Speichern", "Folgen", "What's New"};

        for (var article : articles) {
            if (likeLimitReached()) {
                break;
            }
            processArticle(article, blacklist);
            Utils.pretendToBeHuman(TimeConstants.ONE_SEC, TimeConstants.ONE_SEC * 2);
        }
    }

    private void processArticle(Locator article, String[] blacklist) {
        scrollToArticle(article);
        if (!shouldSkipActivity(article, blacklist)) {
            Locator likeButton;
            try {
                likeButton = findLikeButton(article);
            } catch (PlaywrightException e) {
                return;
            }
            pretendToBeHuman(TimeConstants.ONE_SEC, TimeConstants.ONE_SEC * 2);
            clickButtonIfNotLiked(likeButton);
        }
    }

    private void clickButtonIfNotLiked(Locator likeButton) {
        var buttonColor = likeButton.evaluate("el => getComputedStyle(el).color").toString();
        var rgb = buttonColor.split("\\(")[1].split("\\)")[0].split(",");
        if (Integer.parseInt(rgb[0].trim()) < RGB_MIN_GREY
                && Integer.parseInt(rgb[1].trim()) < RGB_MIN_GREY
                && Integer.parseInt(rgb[2].trim()) < RGB_MIN_GREY) {
            performLike(likeButton);
        }
    }

    private Locator findLikeButton(Locator article) {
        Locator likeButton = article.locator("[data-test-id='like-button']").first();
        if (likeButton.count() == 0) {
            throw new PlaywrightException("Like button not found");
        }
        return likeButton;
    }

    protected boolean shouldSkipActivity(Locator activityDiv, String[] blacklist) {
        if (activityDiv == null || blacklist == null) {
            return true;
        }
        String text = activityDiv.innerText();
        for (String blackListItem : blacklist) {
            if (text.contains(blackListItem)) {
                return true;
            }
        }
        return false;
    }

    private List<Locator> collectArticles(Page page, Locator element) {
        var iteration1 = collectArticleLocators(element);
        if (!iteration1.isEmpty()) {
            scrollToBottom(page, iteration1.getLast());
        }
        var iteration2 = collectArticleLocators(element);
        if (!iteration2.isEmpty()) {
            scrollToBottom(page, iteration2.getLast());
        }
        var iteration3 = collectArticleLocators(element);
        return merge(iteration1, iteration2, iteration3);
    }

    private List<Locator> collectArticleLocators(Locator element) {
        Locator articlesLocator = element.locator("article");
        int count = articlesLocator.count();
        List<Locator> articles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            articles.add(articlesLocator.nth(i));
        }
        return articles;
    }

    @SafeVarargs
    private List<Locator> merge(List<Locator>... lists) {
        List<Locator> finalList = new ArrayList<>();
        for (List<Locator> list : lists) {
            finalList.addAll(list);
        }

        return finalList.stream().distinct().toList();
    }

    private void scrollToBottom(Page page, Locator last) {
        scrollToArticle(last);
        page.evaluate("window.scrollBy(0, 10 * 24)");
        pretendToBeHuman(2);
    }

    private void scrollToArticle(Locator last) {
        last.scrollIntoViewIfNeeded();
    }
}
