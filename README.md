# RepoServGenPlug

This is the maven plugin that generates dynamic repositories services for Quentity entities.

## Concept
It reads the java code using Java parser then generates the necessary constructors and repositories and services for each entity.

## Output
It generates for each entity:
* Entity Repository.
* Entity Service.
* The necessary imports.
* The missing constructors no-args and with-service if needed.

## Example
We have defined entities in myview package:

<br>myview/
<br>├── Human
<br>└── Item

### Human class
```java
@jakarta.persistence.Entity
@Component
public class Human extends Entity<Human> {

    public FldString name, age, address;

    public void define() {
        name.setMaxLength(5).setMask("^[\\s\\w]+$");
        age.setMaxLength(5).setMask("^[\\s\\w]+$");
        address.setMaxLength(5).setMask("^[\\s\\w]+$");
    }
}
```


The generated code:
### Human class (modified)
 ```java
 @jakarta.persistence.Entity
@Component
public class Human extends Entity<Human> {

  public FldString name, age, address;

  public void define() {
    name.setMaxLength(5).setMask("^[\\s\\w]+$");
    age.setMaxLength(5).setMask("^[\\s\\w]+$");
    address.setMaxLength(5).setMask("^[\\s\\w]+$");
  }

  @Autowired()
  public Human(EntityService<Human> entityService) {
    super(entityService);
  }

  public Human() {
    super();
  }
}
 ```

### Human class repository
```java
import com.quentity.entity.EntityRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HumanRepository extends EntityRepository<Human> {
}

```

### Human class service
```java
import com.quentity.entity.EntityService;
import com.quentity.views.myview.HumanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HumanService extends EntityService<Human> {

  @Autowired
  public HumanService(HumanRepository repository) {
    super(repository);
  }
}

```