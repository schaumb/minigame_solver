package solvers;

import bxlx.QuizSolver;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class NNGProgQuizSolver extends QuizSolver<NNGProgQuizSolver.AnswerState> {
    private ArrayList<String> questionWas;
    private int unknownAnswer = 0;

    public NNGProgQuizSolver(String url) {
        super(url);
    }

    @Override
    public State getStateFromContext(SearchContext context) {
        if (context.findElements(By.className("task__title")).size() == 0) {
            if (context.findElements(By.className("result__more")).size() == 0) {
                return State.START;
            } else if (questionWas != null) {
                return State.SOLUTIONS;
            } else {
                return State.END;
            }
        } else {
            return State.QUESTION;
        }
    }

    @Override
    public ExpectedCondition<?> handleStart(SearchContext context) {
        context.findElement(By.className("button__primary")).click();
        questionWas = new ArrayList<>();
        unknownAnswer = 0;
        return ExpectedConditions.numberOfElementsToBeMoreThan(By.className("task__answer-text"), 0);
    }

    @Override
    public ExpectedCondition<?> handleQuestion(SearchContext context) {
        String question = context.findElement(By.className("task__title")).getAttribute("innerHTML");
        List<WebElement> code = context.findElements(By.className("task__code"));
        if (code.size() > 0) {
            question += "\n" + code.get(0).getAttribute("innerHTML").replaceAll("\\s+", "");
        }
        questionWas.add(question);

        List<WebElement> elements = context.findElements(By.className("task__answer-text"));

        results.putIfAbsent(question, new HashMap<>());
        HashMap<String, AnswerState> resultStates = results.get(question);

        List<String> innerHTMLs = elements.stream().map(e -> e.getAttribute("innerHTML")).collect(Collectors.toList());

        innerHTMLs.forEach(s ->
                resultStates.putIfAbsent(s, AnswerState.UNKNOWN));


        resultStates.entrySet().stream()
                .peek(e -> {
                    if (e.getValue() == AnswerState.CHECKED)
                        e.setValue(AnswerState.UNKNOWN);
                })
                .min(Comparator.comparingInt(e -> e.getValue().ordinal())).ifPresent(e ->
                IntStream.range(0, innerHTMLs.size()).filter(i -> innerHTMLs.get(i).equals(e.getKey())).mapToObj(elements::get).findFirst().ifPresent(w -> {
                    if (e.getValue() == AnswerState.UNKNOWN) {

                        ++unknownAnswer;
                        e.setValue(AnswerState.CHECKED);
                    }
                    w.click();
                }));

        context.findElement(By.className("button__primary")).click();

        return ExpectedConditions.or(ExpectedConditions.and(
                ExpectedConditions.not(ExpectedConditions.attributeToBe(By.className("task__title"), "innerHTML", question)),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("task__answer-text"), 0)),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("result__more"), 0));
    }

    @Override
    public ExpectedCondition<?> handleSolutions(SearchContext context) {
        List<WebElement> elements = context.findElements(By.className("result-pop__answers-item"));
        if (elements.size() == 0) {
            context.findElement(By.className("result__more")).click();
            return ExpectedConditions.numberOfElementsToBeMoreThan(By.className("result-pop__answers-item"), 0);
        }

        if (elements.size() != questionWas.size()) {
            System.err.println("ERROR");
            throw new RuntimeException("ERROR");
        }
        for (int i = 0; i < elements.size(); ++i) {
            boolean good = elements.get(i).findElements(By.className("result-pop__hex--eight")).size() == 0;

            results.get(questionWas.get(i)).entrySet().stream()
                    .filter(e -> e.getValue() == AnswerState.CHECKED)
                    .findFirst().ifPresent(e -> e.setValue(good ? AnswerState.GOOD : AnswerState.BAD));
        }
        context.findElement(By.className("result-pop__close")).click();
        questionWas = null;

        return ExpectedConditions.numberOfElementsToBe(By.className("result-pop__answers-item"), 0);
    }

    @Override
    public ExpectedCondition<?> handleEnd(SearchContext context) {
        System.err.println("Unknown answer from this test: " + unknownAnswer);
        long allUnknown = results.values().stream().flatMap(m -> m.values().stream())
                .filter(e -> e == AnswerState.UNKNOWN).count();
        System.err.println("All unknown answer: " + allUnknown);

        if (unknownAnswer > 0 || allUnknown > 0) {
            context.findElement(By.className("result__newgame")).click();
        }

        questionWas = new ArrayList<>();
        unknownAnswer = 0;
        return ExpectedConditions.numberOfElementsToBeMoreThan(By.className("task__answer-text"), 0);
    }

    public enum AnswerState {
        CHECKED,
        UNKNOWN,
        GOOD,
        BAD
    }
}
