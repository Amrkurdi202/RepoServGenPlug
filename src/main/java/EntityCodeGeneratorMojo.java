import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;


import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "RepoServGenPlug", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class EntityCodeGeneratorMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project.basedir}/src/main/java", required = true)
  private String sourceDirectory;
  @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
  private String classesDirectory;

  @Parameter(defaultValue = "${project.build.directory}/generated-sources/gen-plug")
  private String outputDirectory;

  @Parameter(defaultValue = "com.master", required = true)
  private String basePackage;

  @Parameter(property = "com.master.entity.Entity", required = true)
  private String entityBaseClass;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  public void execute() {
    try {
      getLog().info("Starting RepoServGenPlug...");
      generateCode();
      getLog().info("RepoServGenPlug finished code generation.");
    } catch (IOException | MojoExecutionException e) {
      getLog().error(e);
    }
  }


  private void generateCode() throws IOException, MojoExecutionException {
    Path generatedSourcePath = Paths.get(outputDirectory);
    Path sourcePath = Paths.get(sourceDirectory);
    getLog().info("Source path: " + sourcePath);
    if (!Files.exists(sourcePath)) {
      throw new MojoExecutionException("Source directory does not exist: " + sourceDirectory);
    }

    List<Path> javaFiles = findJavaFiles(sourcePath);
    for (Path javaFile : javaFiles) {
      JavaParser javaParser = new JavaParser();
      ParseResult<CompilationUnit> compilationUnit = javaParser.parse(javaFile);
      compilationUnit.getResult().stream().findFirst().ifPresent(c -> {
        getLog().info("Processing file: " + javaFile);
        c.getClassByName(javaFile.getFileName().toString().replace(".java", "")).ifPresent(clazz -> {
          getLog().info("Processing class: " + clazz.getNameAsString());
          if (isSubclassOfEntity(clazz)) {
            String className = clazz.getNameAsString();
            String packageName = clazz.findCompilationUnit().flatMap(CompilationUnit::getPackageDeclaration)
                    .map(NodeWithName::getNameAsString).orElse("");

            boolean hasServiceConstructor = clazz.getConstructors().stream()
                    .anyMatch(constructor -> constructor.getParameters().stream()
                            .anyMatch(param -> param.getTypeAsString().equals("EntityService<" + className + ">")));
            getLog().info("hasServiceConstructor: " + hasServiceConstructor);
            boolean hasDefaultConstructor = clazz.getConstructors().stream()
                    .anyMatch(constructor -> constructor.getParameters().isEmpty());
            getLog().info("hasDefaultConstructor: " + hasDefaultConstructor);
            if (!hasServiceConstructor) {
              getLog().info("Adding service constructor to class: " + clazz.getNameAsString());
              addServiceConstructor(clazz, className);
            }
            if (!hasDefaultConstructor) {
              getLog().info("Adding default constructor to class: " + clazz.getNameAsString());
              addDefaultConstructor(clazz, className);
            }

            // Generate Repository and Service classes
            try {
              generateRepositoryClass(packageName, className);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            try {
              generateServiceClass(packageName, className);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }

            // Add imports to the entity class
            addImportsToEntity(c, packageName, className);

            try {
              Files.write(javaFile.toAbsolutePath(), c.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
              throw new RuntimeException("Error writing updated entity file", e);
            }
          }
        });
      });
    }
  }

  private List<Path> findJavaFiles(Path path) throws IOException {
    try (Stream<Path> paths = Files.walk(path)) {
      return paths.filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".java"))
              .collect(Collectors.toList());
    }
  }

  private boolean isSubclassOfEntity(ClassOrInterfaceDeclaration c) {
    return c.getExtendedTypes().stream()
            .anyMatch(type -> {
              String nameAsString = type.getNameAsString();
              String[] split = entityBaseClass.split("\\.");
              return nameAsString.equals(split[split.length - 1]);
            });
  }

  private void addServiceConstructor(ClassOrInterfaceDeclaration c, String className) {
    ConstructorDeclaration constructor = c.addConstructor(Modifier.Keyword.PUBLIC);
    constructor.addParameter("EntityService<" + className + ">", "entityService");
    constructor.addAnnotation("Autowired");

    BlockStmt body = new BlockStmt();
    body.addStatement(new ExpressionStmt(new NameExpr("super(entityService);")));
    constructor.setBody(body);
  }

  private void addDefaultConstructor(ClassOrInterfaceDeclaration c, String className) {
    ConstructorDeclaration constructor = c.addConstructor(Modifier.Keyword.PUBLIC);
    BlockStmt body = new BlockStmt();
    body.addStatement(new ExpressionStmt(new NameExpr("super();")));
    constructor.setBody(body);
  }

  private void generateRepositoryClass(String packageName, String className) throws IOException {
    String repositoryName = className + "Repository";
    Path outputDir = Paths.get(outputDirectory, packageName.replace('.', '/'));
    Files.createDirectories(outputDir);
    Path filePath = outputDir.resolve(repositoryName + ".java");
    getLog().info("Writing repository file: " + filePath);
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
      writer.println("package " + packageName + ";");
      writer.println();
      writer.println("import " + basePackage + ".entity" + ".EntityRepository;");
      writer.println("import org.springframework.stereotype.Repository;");
      writer.println();
      writer.println("@Repository");
      writer.println("public interface " + repositoryName + " extends EntityRepository<" + className + "> {");
      writer.println("}");
    }
  }

  private void generateServiceClass(String packageName, String className) throws IOException {
    String serviceName = className + "Service";
    String repositoryName = className + "Repository";
    Path outputDir = Paths.get(outputDirectory, packageName.replace('.', '/'));
    Files.createDirectories(outputDir);
    Path filePath = outputDir.resolve(serviceName + ".java");
    getLog().info("Writing service file: " + filePath);
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
      writer.println("package " + packageName + ";");
      writer.println();
      writer.println("import " + basePackage+ ".entity" + ".EntityService;");
      writer.println("import " + packageName + "." + repositoryName + ";");
      writer.println("import jakarta.persistence.EntityManager;");
      writer.println("import org.springframework.beans.factory.annotation.Autowired;");
      writer.println("import org.springframework.stereotype.Service;");
      writer.println();
      writer.println("@Service");
      writer.println("public class " + serviceName + " extends EntityService<" + className + "> {");
      writer.println();
      writer.println("    @Autowired");
      writer.println("    public " + serviceName + "(" + repositoryName + " repository, EntityManager entityManager) {");
      writer.println("        super(repository, entityManager, "+className+".class);");
      writer.println("    }");
      writer.println("}");
    }
  }

  private void addImportsToEntity(CompilationUnit compilationUnit, String packageName, String className) {
    compilationUnit.addImport(basePackage + ".entity" + ".EntityService");
    compilationUnit.addImport("org.springframework.beans.factory.annotation.Autowired");
  }
}
