package de.gn0m3r4l.garminliker.customActions;

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import de.gn0m3r4l.garminliker.actions.FitnessCheckActions;
import de.gn0m3r4l.garminliker.actions.FitnessCheckerActionsImpl;
import de.gn0m3r4l.garminliker.utils.TimeConstants;
import de.gn0m3r4l.garminliker.utils.Utils;
import static de.gn0m3r4l.garminliker.utils.Utils.pretendToBeHuman;

public class KomootChecker extends FitnessCheckerActionsImpl {
    public static final int RGB_MIN_GREY = 35;
    private static KomootChecker checker;

    /**
     * Constructor initializes the provider name for logging purposes.
     */

    public KomootChecker() {
        this.providerName = "Komoot";
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
    protected void runUrl(WebDriver driver, String url) {
        driver.get(url);
        pretendToBeHuman(TimeConstants.DELAY_AFTER_PAGE_LOAD);

        var element = waitForMainPageById(driver, "pageMountNode");

        List<WebElement> articles = collectArticles(driver, element);

        String[] blacklist = new String[]{"Speichern", "Folgen", "What's New"};

        for (var article : articles) {
            processArticle(driver, article, blacklist);
            Utils.pretendToBeHuman(TimeConstants.ONE_SEC, TimeConstants.ONE_SEC * 2);
        }
    }

    private void processArticle(WebDriver driver, WebElement article, String[] blacklist) {
        scrollToArticle(driver, article);
        if (!shouldSkipActivity(article, blacklist)) {
            WebElement likeButton;
            try {
                likeButton = findLikeButton(article);
            } catch (NoSuchElementException e) {
                return;
            }
            pretendToBeHuman(TimeConstants.ONE_SEC, TimeConstants.ONE_SEC * 2);
            clickButtonIfNotLiked(likeButton);
        }
    }

    private void clickButtonIfNotLiked(WebElement likeButton) {
        var buttonColor = likeButton.getCssValue("color"); //rgba(28, 26, 24, 1) grey
        var rgb = buttonColor.split("\\(")[1].split("\\)")[0].split(",");
        if (Integer.parseInt(rgb[0].trim()) < RGB_MIN_GREY
                && Integer.parseInt(rgb[1].trim()) < RGB_MIN_GREY
                && Integer.parseInt(rgb[2].trim()) < RGB_MIN_GREY) {
            performLike(likeButton);
        }
    }

    private WebElement findLikeButton(WebElement article) throws NoSuchElementException {
        return article.findElement(new By.ByCssSelector("[data-test-id='like-button']"));
    }

    protected boolean shouldSkipActivity(WebElement activityDiv, String[] blacklist) {
        if (activityDiv == null || blacklist == null) {
            return true;
        }
        for (String blackListItem : blacklist) {
            if (activityDiv.getText().contains(blackListItem)) {
                return true;
            }
        }
        return false;
    }

    private List<WebElement> collectArticles(WebDriver driver, WebElement element) {
        var iteration1 = element.findElements(new By.ByTagName("article")).stream().toList();
        if (!iteration1.isEmpty()) {
            scrollToBottom(driver, iteration1.getLast());
        }
        var iteration2 = element.findElements(new By.ByTagName("article")).stream().toList();
        if (!iteration2.isEmpty()) {
            scrollToBottom(driver, iteration2.getLast());
        }
        var iteration3 = element.findElements(new By.ByTagName("article")).stream().toList();
        return merge(iteration1, iteration2, iteration3);
    }

    @SafeVarargs
    private List<WebElement> merge(List<WebElement>... lists) {
        List<WebElement> finalList = new ArrayList<>();
        for (List<WebElement> list : lists) {
            finalList.addAll(list);
        }

        return finalList.stream().distinct().toList();
    }

    private void scrollToBottom(WebDriver driver, WebElement last) {
        scrollToArticle(driver, last);
        // scroll additional ten lines
        ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 10 * 24);"); // 24px Zeilenhöhe angenommen
        pretendToBeHuman(2);
    }

    private void scrollToArticle(WebDriver driver, WebElement last) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({behavior:'instant', " +
                "block:'center', inline:'center'});", last);
    }
}
