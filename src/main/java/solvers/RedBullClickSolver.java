package solvers;

import bxlx.QuizSolver;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

public class RedBullClickSolver extends QuizSolver {
    public RedBullClickSolver(String url) {
        super(url);
    }

    @Override
    public State getStateFromContext(SearchContext context) {
        List<WebElement> startElement = context.findElements(By.className("intro-screen-cta"));
        if(startElement.size() > 0 && startElement.get(0).isDisplayed())
            return State.START;
        List<WebElement> retryElement = context.findElements(By.className("game-over-retry"));
        if(retryElement.size() > 0 && retryElement.get(0).isDisplayed())
            return State.SOLUTIONS;

        return State.QUESTION;
    }

    @Override
    public ExpectedCondition<?> handleStart(SearchContext context) {
        context.findElement(By.className("intro-screen-cta")).click();
        return ExpectedConditions.or(
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("thought-bubble-winner"), 0),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("thought-bubble-joker"), 0),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("joker-icon"), 0));
    }

    @Override
    public ExpectedCondition<?> handleQuestion(SearchContext context) {
        try {
            for (WebElement webElement : context.findElements(By.className("joker-icon"))) {
                if (webElement.isDisplayed() && !webElement.getAttribute("class").contains("thought-bubble-leave")) {
                    webElement.click();
                }
            }
            for (WebElement webElement : context.findElements(By.className("thought-bubble-winner"))) {
                if (webElement.isDisplayed() && !webElement.getAttribute("class").contains("thought-bubble-leave")) {
                    webElement.click();
                }

            }
            for (WebElement webElement : context.findElements(By.className("thought-bubble-joker"))) {
                if (webElement.isDisplayed() && !webElement.getAttribute("class").contains("thought-bubble-leave")) {
                    webElement.click();
                }
            }

        } catch (WebDriverException ignored) {
        }

        return ExpectedConditions.or(
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("thought-bubble-winner"), 0),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("thought-bubble-joker"), 0),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("joker-icon"), 0),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("game-over-retry"), 0));
    }

    @Override
    public ExpectedCondition<?> handleSolutions(SearchContext context) {
        int score = 0;
        for (WebElement webElement : context.findElements(By.className("score-value-digit"))) {
            score = score * 10 + Integer.parseInt(webElement.getAttribute("innerHTML"));
        }
        if(score < 1354) {
            System.err.println("Pont: " + score);
            context.findElement(By.className("game-over-retry")).click();
        }

        return ExpectedConditions.or(
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("thought-bubble-winner"), 0),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("thought-bubble-joker"), 0),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("joker-icon"), 0));
    }

    @Override
    public ExpectedCondition<?> handleEnd(SearchContext context) {
        return null;
    }
}
