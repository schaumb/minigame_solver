package solvers;

import bxlx.QuizSolver;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EcosimMinisim extends QuizSolver<EcosimMinisim.GraphNode> {
    private final Function<SearchContext, Integer> calculatePoint;
    private final By restart;
    private final By startGame;
    private final By question;
    private String startNode;
    private Map.Entry<String, GraphNode> prevEntry;
    private int prevPoint;
    private boolean knowEverything;
    private boolean firstStart = true;

    public EcosimMinisim(String url) {
        super(url);
        By restart = By.id("restart-game");
        By startGame = By.id("start-game");
        By question = By.id("question-text");
        Function<SearchContext, Integer> calculatePoint = null;

        boolean kari = url.contains("karacsonyi_jatek_2019");
        boolean miki = url.contains("mikulas");
        if (url.contains("logisztika") || url.contains("minijatek") || miki || kari) {
            boolean insu = url.contains("insurace.hu");
            calculatePoint = context -> {
                List<Integer> collect = context.findElements(By.className("kpi-circle")).stream().map(e -> Integer.parseInt(e.getAttribute("data-pct"))).collect(Collectors.toList());
                if(miki || kari) {
                    return (miki ? -1 : 1) * collect.get(0) + 3 * collect.get(1) + 2 * collect.get(2);
                }
                return (insu ? 2 : 1) * (collect.get(0) + collect.get(1) * 2) + collect.get(2);
            };
            if (kari) {
                restart = By.id("endReStartGameBtn");
                startGame = By.id("startGameBtn");
                question = By.id("question");
            }
        } else if (url.contains("minisim")) {
            final boolean dmb = url.contains("diakverseny");
            if (!dmb)
                restart = By.name("play-again");

            calculatePoint = context -> {
                List<String> collect = context.findElements(By.className("xs-chart")).stream().filter(WebElement::isDisplayed).limit(3).map(e -> e.findElement(By.tagName("span")).getAttribute("innerHTML")).collect(Collectors.toList());

                if (collect.size() < 3) {
                    collect = context.findElements(By.className("circle")).stream().limit(3).map(e -> e.getAttribute("innerHTML")).collect(Collectors.toList());
                }

                return Math.round(Float.parseFloat(collect.get(0)) * 10 + Float.parseFloat(collect.get(1)) * (dmb ? 0 : 200) + Float.parseFloat(collect.get(2)) * 10);
            };
        }


        this.restart = restart;
        this.startGame = startGame;
        this.question = question;
        this.calculatePoint = calculatePoint;
    }

    @Override
    public State getStateFromContext(SearchContext context) {
        if (firstStart) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            firstStart = false;
        }

        List<WebElement> startGames = context.findElements(startGame);
        if (startGames.size() > 0 && startGames.get(0).isDisplayed())
            return State.START;
        List<WebElement> question = context.findElements(By.className("answer"));
        if (question.size() > 0 && question.get(0).isDisplayed()) {
            return State.QUESTION;
        }
        List<WebElement> restart = context.findElements(this.restart);
        if (restart.size() > 0 && restart.get(0).isDisplayed()) {
            if (prevEntry != null) {
                return State.SOLUTIONS;
            } else {
                return State.END;
            }
        }

        return null;
    }

    @Override
    public ExpectedCondition<?> handleStart(SearchContext context) {
        startNode = null;
        prevEntry = null;
        prevPoint = -1;
        knowEverything = true;

        for (String byName : Arrays.asList("teamname", "company", "nickname")) {
            List<WebElement> teamnames = context.findElements(By.name(byName));
            for (WebElement teamname : teamnames) {
                if (!teamname.isDisplayed())
                    continue;
                String uuid = UUID.randomUUID().toString().replaceAll("-", "");
                teamname.sendKeys(uuid);

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        }

        context.findElement(startGame).click();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }

        return ExpectedConditions.and(
                ExpectedConditions.invisibilityOfElementLocated(startGame),
                ExpectedConditions.numberOfElementsToBeMoreThan(By.className("answer"), 0));
    }

    @Override
    public ExpectedCondition<?> handleQuestion(SearchContext context) {
        String question = context.findElement(this.question).getAttribute("innerHTML");

        // calculate point
        int nowPoint = calculatePoint.apply(context);

        if (startNode == null) {
            startNode = question;
        } else if (prevEntry.getValue() == null) {
            prevEntry.setValue(new GraphNode(question, nowPoint - prevPoint));
        } else if (prevEntry.getValue().getPointGrow() != nowPoint - prevPoint) {
            // synchronizing problem.
            System.err.println("Synchronizing problem :( Prev add: " + prevEntry.getValue().getPointGrow() + ", now: " + (nowPoint - prevPoint));
            List<WebElement> elements = context.findElements(By.className("fa-refresh"));
            if (elements.size() > 0 && elements.get(0).isDisplayed()) {
                prevEntry.setValue(null);

                elements.get(0).click();

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }

                return ExpectedConditions.and(ExpectedConditions.visibilityOfElementLocated(startGame));
            } else {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                int otherNowPoint = calculatePoint.apply(context);
                if (otherNowPoint != nowPoint) {
                    return handleStart(context);
                }

                prevEntry.setValue(new GraphNode(prevEntry.getValue().getNextQuestion(), otherNowPoint - prevPoint));
            }
        }
        List<WebElement> elements = context.findElements(By.xpath("//div[starts-with(@id,'answerSelectBlock_')]")).stream().filter(WebElement::isDisplayed).collect(Collectors.toList());

        boolean selector = !elements.isEmpty();
        List<WebElement> answers;
        List<String> answersStr;
        if (selector) {
            // some new stuff ->
            answersStr = new ArrayList<>();
            // tfh...
            List<String> id = elements.stream().map(e -> e.findElement(By.tagName("p")).getText()).collect(Collectors.toList());
            new PermutationGenerator<>(elements.get(0).findElements(By.tagName("option")).stream().map(e -> Integer.parseInt(e.getAttribute("value"))).filter(i -> i != 0).collect(Collectors.toList())).GetAll()
                    .forEach(s -> {
                Map<String, Integer> oneSelection = new HashMap<>();
                s.forEach(new Consumer<>() {
                    int index = 0;
                    @Override
                    public void accept(Integer integer) {
                        oneSelection.put(id.get(index++), integer);
                    }
                });
                answersStr.add(oneSelection.entrySet().stream().map(e-> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("|")));
            });

            answers = elements;
        } else {
            answers = context.findElements(By.className("answer"));
            answersStr = answers.stream().filter(WebElement::isDisplayed).map(e -> e.findElement(By.className("answer-text")).getAttribute("innerHTML")).collect(Collectors.toList());
        }


        results.computeIfAbsent(question, q -> {

            HashMap<String, GraphNode> map = new HashMap<>();
            answersStr.forEach(s -> map.put(s, null));
            return map;
        });
        if (question.equals("Sajnáljuk, hogy el akarsz menni, pedig úgy láttuk, jól csináltad! Nem próbálod meg a következő szezont?")) {
            results.get(question).entrySet().forEach(e -> e.setValue(new GraphNode(null, 0)));
        }

        String calculatedNext = getNextNode(question);

        if (calculatedNext == null) {
            throw new IllegalArgumentException("Logical issue!! getNextNode :(");
        }

        prevEntry = results.get(question).entrySet().stream().filter(e -> e.getKey().equals(calculatedNext)).findFirst().orElse(null);
        if (prevEntry == null) {
            throw new IllegalArgumentException("Logical issue!! null entry :(");
        }
        if (selector) {
            Map<String, Integer> collect = Arrays.stream(calculatedNext.split("\\|")).map(s -> s.split("=")).collect(Collectors.toMap(s -> s[0], s -> Integer.parseInt(s[1])));
            System.err.println("Selected: " + collect);
            for (WebElement element : elements) {
                Integer p = collect.get(element.findElement(By.tagName("p")).getText());
                new Select(element.findElement(By.tagName("select"))).selectByValue(p.toString());
            }
        } else {
            IntStream.range(0, answers.size()).filter(i -> answersStr.get(i).equals(calculatedNext)).mapToObj(answers::get)
                    .findFirst().ifPresent(WebElement::click);
        }
        prevPoint = nowPoint;

        for (WebElement sab : context.findElements(By.id("submitAnswerBtn"))) {
            if (sab.isDisplayed())
                sab.click();
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }

        return ExpectedConditions.or(
                ExpectedConditions.not(ExpectedConditions.attributeToBe(this.question, "innerHTML", question)),
                ExpectedConditions.numberOfElementsToBeMoreThan(restart, 0));
    }

    private String getNextNode(String question) {
        // get closest unknown routey
        Map<String, String> parent = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(question);

        while (!queue.isEmpty()) {
            if (results.get(queue.peek()) == null) {
                System.err.println("WTF");
            }
            String nextCheck = queue.remove();
            for (Map.Entry<String, GraphNode> e : results.get(nextCheck).entrySet()) {
                if (e.getValue() == null) {
                    knowEverything = false;
                    System.err.println("has unknownButton: \"" + nextCheck.substring(0, 10) + "...\" \"" + e.getKey().substring(0, 10) + "\"");
                    String after = nextCheck;
                    while (parent.get(nextCheck) != null) {
                        after = nextCheck;
                        nextCheck = parent.get(nextCheck);
                    }
                    if (after.equals(nextCheck)) {
                        return e.getKey();
                    }

                    String finalAfter = after;
                    String to = results.get(nextCheck).entrySet().stream().filter(en -> en.getValue().getNextQuestion().equals(finalAfter))
                            .map(Map.Entry::getKey)
                            .findFirst().orElse(null);

                    System.err.println("To dest: \"" + question.substring(0, 10) + "...\" answer: \"" + (to == null ? null : to.substring(0, 10)) + "...\"");

                    if (to == null) {
                        throw new IllegalArgumentException("Logical issue nextNode parents");
                    }
                    return to;
                } else if (e.getValue().getNextQuestion() != null && !parent.containsKey(e.getValue().getNextQuestion())) {
                    queue.add(e.getValue().getNextQuestion());
                    parent.put(e.getValue().getNextQuestion(), nextCheck);
                }
            }
        }

        String toMax = results.get(question).entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().getPointGrow() + weight(e.getValue().getNextQuestion()))).map(Map.Entry::getKey).orElse(null);
        System.err.println("To max point: \"" + question.substring(0, 10) + "...\" answer: \"" + (toMax == null ? null : toMax.substring(0, 10)) + "...\"");
        return toMax;
    }

    private int weight(String question) {
        if (question == null)
            return 0;

        return results.get(question).values().stream().mapToInt(graphNode -> graphNode.getPointGrow() + weight(graphNode.getNextQuestion())).max().orElse(0);
    }

    @Override
    public ExpectedCondition<?> handleSolutions(SearchContext context) {
        int nowPoint = calculatePoint.apply(context);
        prevEntry.setValue(new GraphNode(null, nowPoint - prevPoint));
        prevEntry = null;

        return e -> true;
    }

    @Override
    public ExpectedCondition<?> handleEnd(SearchContext context) {
        if (!knowEverything) {
            context.findElement(restart).click();
        }

        startNode = null;
        prevEntry = null;
        prevPoint = -1;
        knowEverything = true;

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
        return ExpectedConditions.or(
                ExpectedConditions.and(ExpectedConditions.visibilityOfElementLocated(startGame)),
                ExpectedConditions.and(
                        ExpectedConditions.invisibilityOfElementLocated(startGame),
                        ExpectedConditions.numberOfElementsToBeMoreThan(By.className("answer"), 0)));
    }

    static class GraphNode implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String nextQuestion;
        private final int pointGrow;

        GraphNode(String nextQuestion, int pointGrow) {
            this.nextQuestion = nextQuestion;
            this.pointGrow = pointGrow;
        }

        public String getNextQuestion() {
            return nextQuestion;
        }

        public int getPointGrow() {
            return pointGrow;
        }

        public boolean isEndNode() {
            return nextQuestion == null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            GraphNode graphNode = (GraphNode) o;

            if (pointGrow != graphNode.pointGrow) return false;
            return nextQuestion != null ? nextQuestion.equals(graphNode.nextQuestion) : graphNode.nextQuestion == null;
        }

        @Override
        public int hashCode() {
            int result = nextQuestion != null ? nextQuestion.hashCode() : 0;
            result = 31 * result + pointGrow;
            return result;
        }

        @Override
        public String toString() {
            return "p:" + pointGrow + " \"" + nextQuestion + "\"";
        }
    }
}
