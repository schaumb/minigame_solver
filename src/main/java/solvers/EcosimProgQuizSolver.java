package solvers;

import bxlx.QuizSolver;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EcosimProgQuizSolver extends QuizSolver<Boolean> {
    private int howManyWasBad = 0;
    private int howManyWasNew = 0;
    private int howManyQuestionWas = 0;
    private boolean handledResults = false;

    public EcosimProgQuizSolver(String url) {
        super(url);
    }

    @Override
    public QuizSolver.State getStateFromContext(SearchContext context) {
        if (context.findElement(By.id("start-game")).isDisplayed())
            return State.START;

        if (!context.findElement(By.id("question-text")).getAttribute("innerHTML").isEmpty())
            return State.QUESTION;

        if (!handledResults)
            return State.SOLUTIONS;

        return State.END;
    }

    @Override
    public ExpectedCondition<Boolean> handleStart(SearchContext context) {
        context.findElement(By.id("start-game")).click();
        howManyWasBad = 0;
        howManyWasNew = 0;
        howManyQuestionWas = 0;
        handledResults = false;
        return ExpectedConditions.not(ExpectedConditions.visibilityOfElementLocated(By.id("start-game")));
    }

    @Override
    public ExpectedCondition<Boolean> handleQuestion(SearchContext context) {
        WebElement question = context.findElement(By.id("question-text"));
        String questionText = question.getAttribute("innerHTML");

        List<WebElement> answers = context.findElement(By.id("answers")).findElements(By.tagName("div"));

        if (results.containsKey(questionText)) {
            // we probably know the answer
            HashMap<String, Boolean> knowResults = results.get(questionText);
            String result = knowResults.entrySet().stream().filter(Map.Entry::getValue).findFirst().map(Map.Entry::getKey).orElse(null);
            boolean success = false;
            if (result != null)
                for (int i = 0; i < answers.size(); i += 3) {
                    if (answers.get(i + 2).getAttribute("innerHTML").contains(result)) {
                        answers.get(i).click();
                        success = true;
                        break;
                    }
                }

            if (!success) {
                System.err.println("WE DIDNT KNOW THE ANSWER :( - " + result);
                for (int i = 0; i < answers.size(); i += 3) {
                    System.err.println(answers.get(i + 2).getAttribute("innerHTML"));
                }
                results.remove(questionText);
                answers.get(0).click();
            }
        } else {
            // guess first
            answers.get(0).click();
            ++howManyWasNew;
        }

        return ExpectedConditions.and(
                ExpectedConditions.not(ExpectedConditions.attributeToBe(question, "innerHTML", questionText)),
                ExpectedConditions.or(ExpectedConditions.not(ExpectedConditions.attributeToBe(question, "innerHTML", "")),
                        ExpectedConditions.numberOfElementsToBeMoreThan(By.className("result-question"), 0)
                ));
    }

    @Override
    public ExpectedCondition<Boolean> handleSolutions(SearchContext context) {
        List<WebElement> resultElements = context.findElement(By.id("answer-result")).findElements(By.tagName("div"));
        String question = null;
        HashMap<String, Boolean> resultBools = null;

        for (WebElement resultElement : resultElements) {
            if (resultElement.getAttribute("class").contains("result-question")) {
                if (question != null) {
                    results.put(question, resultBools);
                    ++howManyQuestionWas;
                }

                question = resultElement.getAttribute("innerHTML");
                resultBools = new HashMap<>();
            } else if (resultElement.getAttribute("class").contains("result-answers")) {
                if (resultBools != null) {
                    resultBools.put(resultElement.getAttribute("innerHTML"),
                            resultElement.getAttribute("class").contains("result-good"));

                    if (resultElement.getAttribute("class").contains("result-bad")) {
                        ++howManyWasBad;
                    }
                }
            }
        }
        if (question != null) {
            results.put(question, resultBools);
            ++howManyQuestionWas;
        }
        handledResults = true;

        return d -> true;
    }

    @Override
    public ExpectedCondition<WebElement> handleEnd(SearchContext context) {
        System.err.println("Questions count: " + howManyQuestionWas + ", bad answers: " + howManyWasBad + ", new questions: " + howManyWasNew);
        if (howManyWasBad > 0) {
            context.findElement(By.id("restart-game")).click();
        }
        return ExpectedConditions.visibilityOfElementLocated(By.id("start-game"));
    }
}