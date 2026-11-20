package com.uniphore.uassist.summary.service;

import java.util.List;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.model.DispositionLevels;
import com.uniphore.uassist.summary.model.MessageResponse;

public interface DispositionService {
  public MessageResponse upsertDisposition(final Disposition DispositionReq);

  public Disposition getDisposition(final String businessProcessId);

  public List<DispositionLevels> getDispositionLevels(final List<String> businessProcessIds);
}
