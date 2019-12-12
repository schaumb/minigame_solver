package bxlx;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;

public class MyMain {
    public static void main(String... args) {
        setDriver();

        String url = args.length > 0 ? args[0] : "https://www.javachallenge.hu/mini/";

        ChromeDriver driver = new ChromeDriver();
        try (QuizSolver<?> quizSolver = QuizSolver.findQuizSolverByUrl(url)) {
            Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(10000), Duration.ofMillis(0));

            driver.get(url);

            while (true) {
                ExpectedCondition<?> expectedCondition = null;

                QuizSolver.State state = quizSolver.getStateFromContext(driver);
                if (state != null)
                    switch (state) {
                        case START:
                            expectedCondition = quizSolver.handleStart(driver);
                            break;
                        case QUESTION:
                            expectedCondition = quizSolver.handleQuestion(driver);
                            break;
                        case SOLUTIONS:
                            expectedCondition = quizSolver.handleSolutions(driver);
                            break;
                        case END:
                            expectedCondition = quizSolver.handleEnd(driver);
                            break;
                    }

                if (expectedCondition != null) {
                    wait.until(expectedCondition);
                } else {
                    break;
                }
            }
        } catch (WebDriverException e) {
            e.printStackTrace();
        } finally {
            try {
                driver.quit();
                driver.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void setDriver() {
        File driverPath = new File("target/driver");
        while (driverPath.isDirectory()) {
            File[] files = driverPath.listFiles();
            if (files == null || files.length == 0) {
                System.err.println("driver not found in target/driver dir. You need to run 'mvn package' command.");
                System.exit(1);
            }
            driverPath = files[0];
        }
        System.setProperty("webdriver.chrome.driver", driverPath.getPath());
    }
}
