import expectations.junit.ExpectationsTestRunner;
import org.junit.runner.RunWith;

@RunWith(expectations.junit.ExpectationsTestRunner.class)
public class AllTests implements ExpectationsTestRunner.TestSource {

    public String testPath() {
        return "test/clojure/ring/adapter/test";
    }
}