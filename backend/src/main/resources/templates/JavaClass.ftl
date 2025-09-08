package ${packageName()};

import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;

import java.util.function.Function;
import java.util.function.Predicate;

public class ${className()} implements ConsumerTransformationRule<${inputType()}> {

<#list methods() as method>
    ${method}
</#list>

}
