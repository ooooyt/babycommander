package com.ooooyt.babycommander.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkeletonExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void testJavaSkeleton() throws IOException {
        Path file = tempDir.resolve("Calculator.java");
        Files.writeString(file, ""
                + "package com.example;\n"
                + "\n"
                + "import java.util.List;\n"
                + "\n"
                + "/**\n"
                + " * Calculator class\n"
                + " */\n"
                + "public class Calculator {\n"
                + "    private String name;\n"
                + "    protected int count;\n"
                + "    public List<String> items;\n"
                + "\n"
                + "    public Calculator(String name) {\n"
                + "        this.name = name;\n"
                + "    }\n"
                + "\n"
                + "    public int add(int a, int b) {\n"
                + "        return a + b;\n"
                + "    }\n"
                + "\n"
                + "    public String getName() {\n"
                + "        return name;\n"
                + "    }\n"
                + "\n"
                + "    private void helper() {\n"
                + "        System.out.println(\"helper\");\n"
                + "    }\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("Java", result.getLanguage());
        assertTrue(result.getSkeleton().contains("public class Calculator"));
        assertTrue(result.getSkeleton().contains("private String name;"));
        assertTrue(result.getSkeleton().contains("protected int count;"));
        assertTrue(result.getSkeleton().contains("public List<String> items;"));
        assertTrue(result.getSkeleton().contains("public Calculator(String name)"));
        assertTrue(result.getSkeleton().contains("public int add(int a, int b)"));
        assertTrue(result.getSkeleton().contains("public String getName()"));
        assertTrue(result.getSkeleton().contains("private void helper()"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("return a + b;"));
        assertFalse(result.getSkeleton().contains("System.out.println"));

        // Functions info
        assertEquals(4, result.getFunctions().size());
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("getName")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("helper")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("Calculator")));
    }

    @Test
    void testPythonSkeleton() throws IOException {
        Path file = tempDir.resolve("calculator.py");
        Files.writeString(file, ""
                + "import math\n"
                + "\n"
                + "\n"
                + "class Calculator:\n"
                + "    \"\"\"Calculator class\"\"\"\n"
                + "\n"
                + "    def __init__(self, name):\n"
                + "        self.name = name\n"
                + "\n"
                + "    def add(self, a, b):\n"
                + "        return a + b\n"
                + "\n"
                + "    def get_name(self):\n"
                + "        return self.name\n"
                + "\n"
                + "\n"
                + "def global_helper(x):\n"
                + "    return x * 2\n"
                + "\n"
                + "# standalone comment\n"
                + "PI = 3.14\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("Python", result.getLanguage());
        assertTrue(result.getSkeleton().contains("class Calculator:"));
        assertTrue(result.getSkeleton().contains("def __init__(self, name):"));
        assertTrue(result.getSkeleton().contains("def add(self, a, b):"));
        assertTrue(result.getSkeleton().contains("def get_name(self):"));
        assertTrue(result.getSkeleton().contains("def global_helper(x):"));
        assertTrue(result.getSkeleton().contains("PI = 3.14"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("return a + b"));
        assertFalse(result.getSkeleton().contains("return self.name"));

        // Functions info
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("__init__")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("get_name")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("global_helper")));
    }

    @Test
    void testJavaScriptSkeleton() throws IOException {
        Path file = tempDir.resolve("calc.js");
        Files.writeString(file, ""
                + "// Constants\n"
                + "const PI = 3.14;\n"
                + "\n"
                + "/**\n"
                + " * Calculator class\n"
                + " */\n"
                + "class Calculator {\n"
                + "    constructor(name) {\n"
                + "        this.name = name;\n"
                + "    }\n"
                + "\n"
                + "    add(a, b) {\n"
                + "        return a + b;\n"
                + "    }\n"
                + "\n"
                + "    getName() {\n"
                + "        return this.name;\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "function globalHelper(x) {\n"
                + "    return x * 2;\n"
                + "}\n"
                + "\n"
                + "const arrowFn = (x) => {\n"
                + "    return x + 1;\n"
                + "};\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("JavaScript", result.getLanguage());
        assertTrue(result.getSkeleton().contains("class Calculator"));
        assertTrue(result.getSkeleton().contains("constructor(name)"));
        assertTrue(result.getSkeleton().contains("add(a, b)"));
        assertTrue(result.getSkeleton().contains("getName()"));
        assertTrue(result.getSkeleton().contains("function globalHelper(x)"));
        assertTrue(result.getSkeleton().contains("const PI = 3.14;"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("return a + b;"));
        assertFalse(result.getSkeleton().contains("return this.name;"));
        assertFalse(result.getSkeleton().contains("return x * 2;"));

        // Functions info
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("getName")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("globalHelper")));
    }

    @Test
    void testGoSkeleton() throws IOException {
        Path file = tempDir.resolve("user.go");
        Files.writeString(file, ""
                + "package main\n"
                + "\n"
                + "import \"fmt\"\n"
                + "\n"
                + "type User struct {\n"
                + "    Name string\n"
                + "    Age  int\n"
                + "}\n"
                + "\n"
                + "func (u *User) Greet() string {\n"
                + "    return \"Hello, \" + u.Name\n"
                + "}\n"
                + "\n"
                + "func NewUser(name string, age int) *User {\n"
                + "    return &User{Name: name, Age: age}\n"
                + "}\n"
                + "\n"
                + "func main() {\n"
                + "    u := NewUser(\"Alice\", 30)\n"
                + "    fmt.Println(u.Greet())\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("Go", result.getLanguage());
        assertTrue(result.getSkeleton().contains("type User struct"));
        assertTrue(result.getSkeleton().contains("func (u *User) Greet() string"));
        assertTrue(result.getSkeleton().contains("func NewUser(name string, age int) *User"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("return \"Hello, \" + u.Name"));
        assertFalse(result.getSkeleton().contains("return &User{Name: name, Age: age}"));

        // Functions info
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("Greet")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("NewUser")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("main")));
    }

    @Test
    void testRubySkeleton() throws IOException {
        Path file = tempDir.resolve("calc.rb");
        Files.writeString(file, ""
                + "# Calculator class\n"
                + "class Calculator\n"
                + "  def initialize(name)\n"
                + "    @name = name\n"
                + "  end\n"
                + "\n"
                + "  def add(a, b)\n"
                + "    a + b\n"
                + "  end\n"
                + "\n"
                + "  def get_name\n"
                + "    @name\n"
                + "  end\n"
                + "end\n"
                + "\n"
                + "def global_helper(x)\n"
                + "  x * 2\n"
                + "end\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("Ruby", result.getLanguage());
        assertTrue(result.getSkeleton().contains("class Calculator"));
        assertTrue(result.getSkeleton().contains("def initialize(name)"));
        assertTrue(result.getSkeleton().contains("def add(a, b)"));
        assertTrue(result.getSkeleton().contains("def get_name"));
        assertTrue(result.getSkeleton().contains("def global_helper(x)"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("@name = name"));
        assertFalse(result.getSkeleton().contains("a + b"));

        // Functions info
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("initialize")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("get_name")));
    }

    @Test
    void testReadFunction() throws IOException {
        Path file = tempDir.resolve("Calculator.java");
        Files.writeString(file, ""
                + "public class Calculator {\n"
                + "    public int add(int a, int b) {\n"
                + "        return a + b;\n"
                + "    }\n"
                + "\n"
                + "    public String getName() {\n"
                + "        return \"test\";\n"
                + "    }\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();

        // Read full function body by name
        String addBody = extractor.readFunction(file.toString(), "add");
        assertTrue(addBody.contains("public int add(int a, int b)"));
        assertTrue(addBody.contains("return a + b;"));
    }

    @Test
    void testReadFunctionByParamCount() throws IOException {
        Path file = tempDir.resolve("Overloaded.java");
        Files.writeString(file, ""
                + "public class Overloaded {\n"
                + "    public void process(String s) {\n"
                + "        System.out.println(s);\n"
                + "    }\n"
                + "\n"
                + "    public void process(String s, int n) {\n"
                + "        System.out.println(s + n);\n"
                + "    }\n"
                + "\n"
                + "    public void process(int a, int b) {\n"
                + "        System.out.println(a + b);\n"
                + "    }\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        // readFunction(file, name, paramCount) - get the 2-param version
        String body = extractor.readFunction(file.toString(), "process", 2);
        assertTrue(body.contains("public void process(String s, int n)")
                || body.contains("public void process(int a, int b)"));
        assertTrue(body.contains("System.out.println"));
    }

    @Test
    void testUnknownLanguage() throws IOException {
        Path file = tempDir.resolve("data.txt");
        Files.writeString(file, ""
                + "Some random text\n"
                + "function foo() {\n"
                + "    print('hello')\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("unknown", result.getLanguage());
        // Should still detect function-like lines
        assertTrue(result.getSkeleton().contains("function foo()"));
    }

    @Test
    void testBraceInsideStringLiteral() throws IOException {
        Path file = tempDir.resolve("StringTest.java");
        Files.writeString(file, ""
                + "public class StringTest {\n"
                + "    public void test() {\n"
                + "        String s = \"if (x) { }\";\n"
                + "        System.out.println(s);\n"
                + "    }\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("public void test()"));
        // The string literal body should be removed
        assertFalse(result.getSkeleton().contains("String s = \"if (x) { }\";"));
    }

    @Test
    void testNestedBraces() throws IOException {
        Path file = tempDir.resolve("NestedTest.java");
        Files.writeString(file, ""
                + "public class NestedTest {\n"
                + "    public void outer() {\n"
                + "        if (true) {\n"
                + "            for (int i = 0; i < 10; i++) {\n"
                + "                System.out.println(i);\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("public void outer()"));
        assertFalse(result.getSkeleton().contains("System.out.println(i)"));
    }

    @Test
    void testEmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.java");
        Files.writeString(file, "");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("Java", result.getLanguage());
        assertTrue(result.getSkeleton().isEmpty());
    }

    @Test
    void testFileNotFound() {
        SkeletonExtractor extractor = new SkeletonExtractor();
        assertThrows(IOException.class, () -> extractor.extractSkeleton("/nonexistent/path/File.java"));
    }

    @Test
    void testAnnotationsPreserved() throws IOException {
        Path file = tempDir.resolve("Annotated.java");
        Files.writeString(file, ""
                + "public class Annotated {\n"
                + "    @Override\n"
                + "    public String toString() {\n"
                + "        return \"annotated\";\n"
                + "    }\n"
                + "\n"
                + "    @Deprecated\n"
                + "    @SuppressWarnings(\"unchecked\")\n"
                + "    public void oldMethod() {\n"
                + "        List l = new ArrayList();\n"
                + "    }\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("@Override"));
        assertTrue(result.getSkeleton().contains("public String toString()"));
        assertTrue(result.getSkeleton().contains("@Deprecated"));
        assertTrue(result.getSkeleton().contains("@SuppressWarnings(\"unchecked\")"));
        assertTrue(result.getSkeleton().contains("public void oldMethod()"));
        assertFalse(result.getSkeleton().contains("return \"annotated\""));
        assertFalse(result.getSkeleton().contains("List l = new ArrayList()"));
    }

    @Test
    void testKotlinSkeleton() throws IOException {
        Path file = tempDir.resolve("calc.kt");
        Files.writeString(file, ""
                + "class Calculator(val name: String) {\n"
                + "    fun add(a: Int, b: Int): Int {\n"
                + "        return a + b\n"
                + "    }\n"
                + "\n"
                + "    fun getName(): String {\n"
                + "        return name\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "fun globalHelper(x: Int): Int {\n"
                + "    return x * 2\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("Kotlin", result.getLanguage());
        assertTrue(result.getSkeleton().contains("class Calculator"));
        assertTrue(result.getSkeleton().contains("fun add(a: Int, b: Int): Int"));
        assertTrue(result.getSkeleton().contains("fun getName(): String"));
        assertTrue(result.getSkeleton().contains("fun globalHelper(x: Int): Int"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("return a + b"));
        assertFalse(result.getSkeleton().contains("return name"));
        assertFalse(result.getSkeleton().contains("return x * 2"));

        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("getName")));
    }

    @Test
    void testCSkeleton() throws IOException {
        Path file = tempDir.resolve("calc.c");
        Files.writeString(file, ""
                + "#include <stdio.h>\n"
                + "\n"
                + "int add(int a, int b) {\n"
                + "    return a + b;\n"
                + "}\n"
                + "\n"
                + "void printResult(int result) {\n"
                + "    printf(\"%d\\n\", result);\n"
                + "}\n"
                + "\n"
                + "int main() {\n"
                + "    int x = add(1, 2);\n"
                + "    printResult(x);\n"
                + "    return 0;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("C", result.getLanguage());
        assertTrue(result.getSkeleton().contains("int add(int a, int b)"));
        assertTrue(result.getSkeleton().contains("void printResult(int result)"));
        assertTrue(result.getSkeleton().contains("int main()"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("return a + b;"));
        assertFalse(result.getSkeleton().contains("printf"));

        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("main")));
    }

    @Test
    void testTypeScriptSkeleton() throws IOException {
        Path file = tempDir.resolve("calc.ts");
        Files.writeString(file, ""
                + "interface Calc {\n"
                + "    add(a: number, b: number): number;\n"
                + "}\n"
                + "\n"
                + "class Calculator implements Calc {\n"
                + "    private name: string;\n"
                + "\n"
                + "    constructor(name: string) {\n"
                + "        this.name = name;\n"
                + "    }\n"
                + "\n"
                + "    add(a: number, b: number): number {\n"
                + "        return a + b;\n"
                + "    }\n"
                + "\n"
                + "    getName(): string {\n"
                + "        return this.name;\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "function globalHelper(x: number): number {\n"
                + "    return x * 2;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("TypeScript", result.getLanguage());
        assertTrue(result.getSkeleton().contains("class Calculator implements Calc"));
        assertTrue(result.getSkeleton().contains("constructor(name: string)"));
        assertTrue(result.getSkeleton().contains("add(a: number, b: number): number"));
        assertTrue(result.getSkeleton().contains("getName(): string"));
        assertTrue(result.getSkeleton().contains("function globalHelper(x: number): number"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("return a + b;"));
        assertFalse(result.getSkeleton().contains("return this.name;"));
        assertFalse(result.getSkeleton().contains("return x * 2;"));

        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("getName")));
    }

    @Test
    void testRustSkeleton() throws IOException {
        Path file = tempDir.resolve("calc.rs");
        Files.writeString(file, ""
                + "struct Calculator {\n"
                + "    name: String,\n"
                + "}\n"
                + "\n"
                + "impl Calculator {\n"
                + "    fn add(&self, a: i32, b: i32) -> i32 {\n"
                + "        a + b\n"
                + "    }\n"
                + "\n"
                + "    fn get_name(&self) -> &str {\n"
                + "        &self.name\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "fn global_helper(x: i32) -> i32 {\n"
                + "    x * 2\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("Rust", result.getLanguage());
        assertTrue(result.getSkeleton().contains("struct Calculator"));
        assertTrue(result.getSkeleton().contains("fn add(&self, a: i32, b: i32) -> i32"));
        assertTrue(result.getSkeleton().contains("fn get_name(&self) -> &str"));
        assertTrue(result.getSkeleton().contains("fn global_helper(x: i32) -> i32"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("a + b"));
        assertFalse(result.getSkeleton().contains("&self.name"));

        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("global_helper")));
    }

    @Test
    void testLombokData() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.Data;\n"
                + "\n"
                + "@Data\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "    private int age;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("@Data"));
        assertTrue(result.getSkeleton().contains("public class User"));
        assertTrue(result.getSkeleton().contains("private String name;"));
        assertTrue(result.getSkeleton().contains("private int age;"));
        assertTrue(result.getSkeleton().contains("Lombok-generated"));
        assertTrue(result.getSkeleton().contains("getName()"));
        assertTrue(result.getSkeleton().contains("setName(String)"));
        assertTrue(result.getSkeleton().contains("getAge()"));
        assertTrue(result.getSkeleton().contains("setAge(int)"));
        assertTrue(result.getSkeleton().contains("toString()"));
        assertTrue(result.getSkeleton().contains("equals()"));
        assertTrue(result.getSkeleton().contains("hashCode()"));
        assertTrue(result.getSkeleton().contains("RequiredArgsConstructor"));
    }

    @Test
    void testLombokBuilder() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.Builder;\n"
                + "import lombok.Data;\n"
                + "\n"
                + "@Data\n"
                + "@Builder\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "    private int age;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("@Data"));
        assertTrue(result.getSkeleton().contains("@Builder"));
        assertTrue(result.getSkeleton().contains("UserBuilder builder()"));
    }

    @Test
    void testLombokGetterSetter() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.Getter;\n"
                + "import lombok.Setter;\n"
                + "\n"
                + "@Getter\n"
                + "@Setter\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "    private boolean active;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("@Getter"));
        assertTrue(result.getSkeleton().contains("@Setter"));
        assertTrue(result.getSkeleton().contains("Lombok-generated"));
        assertTrue(result.getSkeleton().contains("getName()"));
        assertTrue(result.getSkeleton().contains("setName(String)"));
        assertTrue(result.getSkeleton().contains("isActive()"));
        assertTrue(result.getSkeleton().contains("setActive(boolean)"));
    }

    @Test
    void testLombokAllArgsConstructor() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.AllArgsConstructor;\n"
                + "\n"
                + "@AllArgsConstructor\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "    private int age;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("AllArgsConstructor"));
        assertTrue(result.getSkeleton().contains("User(String name, int age)"));
    }

    @Test
    void testLombokNoArgsConstructor() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.NoArgsConstructor;\n"
                + "\n"
                + "@NoArgsConstructor\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("NoArgsConstructor"));
        assertTrue(result.getSkeleton().contains("User()"));
    }

    @Test
    void testLombokValue() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.Value;\n"
                + "\n"
                + "@Value\n"
                + "public class User {\n"
                + "    String name;\n"
                + "    int age;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("@Value"));
        assertTrue(result.getSkeleton().contains("Lombok-generated"));
        assertTrue(result.getSkeleton().contains("getName()"));
        assertTrue(result.getSkeleton().contains("getAge()"));
        assertTrue(result.getSkeleton().contains("toString()"));
        assertTrue(result.getSkeleton().contains("equals()"));
        assertTrue(result.getSkeleton().contains("hashCode()"));
        assertTrue(result.getSkeleton().contains("AllArgsConstructor"));
        // @Value should NOT generate setters
        assertFalse(result.getSkeleton().contains("setName"));
        assertFalse(result.getSkeleton().contains("setAge"));
    }

    @Test
    void testLombokNoFields() throws IOException {
        Path file = tempDir.resolve("Empty.java");
        Files.writeString(file, ""
                + "import lombok.NoArgsConstructor;\n"
                + "import lombok.Builder;\n"
                + "\n"
                + "@NoArgsConstructor\n"
                + "@Builder\n"
                + "public class Empty {\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("NoArgsConstructor"));
        assertTrue(result.getSkeleton().contains("Empty()"));
        assertTrue(result.getSkeleton().contains("EmptyBuilder builder()"));
    }

    @Test
    void testLombokWithExistingMethods() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.Data;\n"
                + "\n"
                + "@Data\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "\n"
                + "    @Override\n"
                + "    public String toString() {\n"
                + "        return name;\n"
                + "    }\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        // Real method should still appear in skeleton
        assertTrue(result.getSkeleton().contains("public String toString()"));
        // Lombok comment should still mention getters
        assertTrue(result.getSkeleton().contains("getName()"));
        assertTrue(result.getSkeleton().contains("setName(String)"));
        // Bodies still stripped
        assertFalse(result.getSkeleton().contains("return name;"));
    }

    @Test
    void testClassWithoutLombok() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "public class User {\n"
                + "    private String name;\n"
                + "    private int age;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        // Should NOT have Lombok-generated header
        assertFalse(result.getSkeleton().contains("Lombok-generated"));
        assertTrue(result.getSkeleton().contains("private String name;"));
    }

    @Test
    void testLombokSlf4j() throws IOException {
        Path file = tempDir.resolve("Logger.java");
        Files.writeString(file, ""
                + "import lombok.extern.slf4j.Slf4j;\n"
                + "\n"
                + "@Slf4j\n"
                + "public class Logger {\n"
                + "    public void logSomething() {\n"
                + "        log.info(\"hello\");\n"
                + "    }\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("@Slf4j"));
        assertTrue(result.getSkeleton().contains("log field"));
    }

    @Test
    void testLombokToString() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.ToString;\n"
                + "\n"
                + "@ToString\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("@ToString"));
        assertTrue(result.getSkeleton().contains("toString()"));
    }

    @Test
    void testLombokEqualsAndHashCode() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.EqualsAndHashCode;\n"
                + "\n"
                + "@EqualsAndHashCode\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("@EqualsAndHashCode"));
        assertTrue(result.getSkeleton().contains("equals()"));
        assertTrue(result.getSkeleton().contains("hashCode()"));
    }

    @Test
    void testLombokMultipleAnnotations() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.*;\n"
                + "\n"
                + "@Getter\n"
                + "@Setter\n"
                + "@AllArgsConstructor\n"
                + "@ToString\n"
                + "@EqualsAndHashCode\n"
                + "public class User {\n"
                + "    private String name;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        String skeleton = result.getSkeleton();
        assertTrue(skeleton.contains("Lombok-generated"));
        assertTrue(skeleton.contains("getName()"));
        assertTrue(skeleton.contains("setName(String)"));
        assertTrue(skeleton.contains("toString()"));
        assertTrue(skeleton.contains("equals()"));
        assertTrue(skeleton.contains("hashCode()"));
        assertTrue(skeleton.contains("User(String name)"));
    }

    @Test
    void testLombokRequiredArgsConstructor() throws IOException {
        Path file = tempDir.resolve("User.java");
        Files.writeString(file, ""
                + "import lombok.RequiredArgsConstructor;\n"
                + "\n"
                + "@RequiredArgsConstructor\n"
                + "public class User {\n"
                + "    private final String name;\n"
                + "    private int age;\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertTrue(result.getSkeleton().contains("RequiredArgsConstructor"));
        assertTrue(result.getSkeleton().contains("User("));
    }

    @Test
    void testSwiftSkeleton() throws IOException {
        Path file = tempDir.resolve("calc.swift");
        Files.writeString(file, ""
                + "class Calculator {\n"
                + "    var name: String\n"
                + "\n"
                + "    init(name: String) {\n"
                + "        self.name = name\n"
                + "    }\n"
                + "\n"
                + "    func add(_ a: Int, _ b: Int) -> Int {\n"
                + "        return a + b\n"
                + "    }\n"
                + "\n"
                + "    func getName() -> String {\n"
                + "        return name\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "func globalHelper(_ x: Int) -> Int {\n"
                + "    return x * 2\n"
                + "}\n");

        SkeletonExtractor extractor = new SkeletonExtractor();
        SkeletonExtractor.SkeletonResult result = extractor.extractSkeleton(file.toString());

        assertEquals("Swift", result.getLanguage());
        assertTrue(result.getSkeleton().contains("class Calculator"));
        assertTrue(result.getSkeleton().contains("init(name: String)"));
        assertTrue(result.getSkeleton().contains("func add(_ a: Int, _ b: Int) -> Int"));
        assertTrue(result.getSkeleton().contains("func getName() -> String"));
        assertTrue(result.getSkeleton().contains("func globalHelper(_ x: Int) -> Int"));

        // Bodies removed
        assertFalse(result.getSkeleton().contains("return a + b"));
        assertFalse(result.getSkeleton().contains("return name"));

        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("add")));
        assertTrue(result.getFunctions().stream().anyMatch(f -> f.getName().equals("getName")));
    }
}
