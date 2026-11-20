package com.uniphore.uassist.summary.util;

import java.util.Random;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RandomUtil {

  public String random() {
    final Random random = new Random();
    random.ints(0, Integer.MAX_VALUE);
    final String randomValue = new StringBuilder().append(Math.abs(random.nextLong()))
        .append(DateUtil.TIME.millis()).toString();
    return randomValue;
  }

}
