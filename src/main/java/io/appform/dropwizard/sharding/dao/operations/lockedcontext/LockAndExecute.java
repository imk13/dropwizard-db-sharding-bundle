package io.appform.dropwizard.sharding.dao.operations.lockedcontext;

import com.google.common.collect.Lists;
import io.appform.dropwizard.sharding.dao.LockedContext.Mode;
import io.appform.dropwizard.sharding.dao.operations.OpContext;
import io.appform.dropwizard.sharding.dao.operations.OpType;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.hibernate.Session;

/**
 * Acquires lock on an entity and perform all the operations given.
 *
 * @param <T> Entity type on which lock is being acquired.
 */
@Data
public class LockAndExecute<T> extends OpContext<T> {

  private final List<Consumer<T>> operations = Lists.newArrayList();
  @NonNull
  private final Mode mode;
  private Supplier<T> getter;
  private Function<T, T> saver;
  private T entity;

  @Builder(builderMethodName = "buildForRead", builderClassName = "ReadModeBuilder")
  public LockAndExecute(@NonNull Supplier<T> getter) {
    this.mode = Mode.READ;
    this.getter = getter;
  }

  @Builder(builderMethodName = "buildForInsert", builderClassName = "InsertModeBuilder")
  public LockAndExecute(@NonNull T entity, @NonNull Function<T, T> saver) {
    this.mode = Mode.INSERT;
    this.entity = entity;
    this.saver = saver;
  }

  @Override
  public T apply(Session session) {
    T result = generateEntity();
    operations
        .forEach(operation -> operation.accept(result));
    return result;
  }

  private T generateEntity() {
    T result = null;
    switch (mode) {
      case READ:
        result = getter.get();
        if (result == null) {
          throw new RuntimeException("Entity doesn't exist");
        }
        break;
      case INSERT:
        result = saver.apply(entity);
        break;
      default:
        break;

    }
    return result;
  }

  @Override
  public OpType getOpType() {
    return OpType.LOCK_AND_EXECUTE;
  }

  @Override
  public <R> R visit(OpContextVisitor<R> visitor) {
    return visitor.visit(this);
  }
}
