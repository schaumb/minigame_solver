package bxlx;

import org.openqa.selenium.SearchContext;
import org.openqa.selenium.support.ui.ExpectedCondition;
import solvers.EcosimMinisim;
import solvers.EcosimProgQuizSolver;
import solvers.NNGProgQuizSolver;

import java.io.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.stream.Collectors;

public abstract class QuizSolver<Element extends Serializable> implements Closeable {
    protected final HashMap<String, HashMap<String, Element>> results;
    private final File file;

    protected QuizSolver(String url) {
        file = new File(Base64.getEncoder().encodeToString(url.getBytes()).replaceFirst("=+", "") + ".txt");
        results = readFile();
    }

    static QuizSolver<?> findQuizSolverByUrl(String url) {
        if (url.endsWith("/mini/")) {
            return new EcosimProgQuizSolver(url);
        }
        if (url.endsWith("nng.com")) {
            return new NNGProgQuizSolver(url);
        }
        if (url.contains("ecosim.hu") || url.contains("diakverseny.hu")) {
            return new EcosimMinisim(url);
        }
        throw new UnsupportedOperationException("No matching quiz solver for url: " + url);
    }

    @SuppressWarnings("unchecked")
    private HashMap<String, HashMap<String, Element>> readFile() {
        if (!file.canRead())
            return new HashMap<>();

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (HashMap<String, HashMap<String, Element>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        if (results.isEmpty())
            return;

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(results);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        } finally {
            System.out.println(this);
        }
    }

    public abstract State getStateFromContext(SearchContext context);

    public abstract ExpectedCondition<?> handleStart(SearchContext context);

    public abstract ExpectedCondition<?> handleQuestion(SearchContext context);

    public abstract ExpectedCondition<?> handleSolutions(SearchContext context);

    public abstract ExpectedCondition<?> handleEnd(SearchContext context);

    @Override
    public String toString() {
        return file.getName() + "\n" +
                new String(Base64.getDecoder().decode(file.getName().substring(0, file.getName().length() - 4))) + "\n" +
                results.entrySet().stream().map(e ->
                        "Question: " + e.getKey() + "\n" +
                                e.getValue().entrySet().stream().map(e2 ->
                                        e2.getValue() + " - " + e2.getKey()
                                ).collect(Collectors.joining("\n"))
                ).collect(Collectors.joining("\n"));
    }


    public enum State {
        START,
        QUESTION,
        SOLUTIONS,
        END
    }
}