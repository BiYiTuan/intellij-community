// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.InspectionTestUtil;
import com.siyeh.ig.redundancy.RedundantStringOperationInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;


public class RedundantStringOperationInspectionTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    List<InspectionProfileEntry> tools =
      InspectionTestUtil.instantiateTools(Collections.singleton(RedundantStringOperationInspection.class));
    
    return new LocalInspectionTool[]{(LocalInspectionTool)tools.iterator().next()};
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/redundantStringOperation";
  }
}