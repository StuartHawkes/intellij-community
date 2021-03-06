// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementBuilder;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralSearchProfile {
  public static final ExtensionPointName<StructuralSearchProfile> EP_NAME =
    ExtensionPointName.create("com.intellij.structuralsearch.profile");
  @NonNls protected static final String PATTERN_PLACEHOLDER = "$$PATTERN_PLACEHOLDER$$";

  public abstract void compile(PsiElement[] elements, @NotNull GlobalCompilingVisitor globalVisitor);

  @NotNull
  public abstract PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor);

  @NotNull
  public abstract NodeFilter getLexicalNodesFilter();

  @NotNull
  public abstract CompiledPattern createCompiledPattern();

  @NotNull
  public List<MatchPredicate> getCustomPredicates(MatchVariableConstraint constraint, String name, MatchOptions options) {
    return Collections.emptyList();
  }

  public abstract boolean isMyLanguage(@NotNull Language language);

  @NotNull
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull LanguageFileType fileType,
                                        @NotNull Language language,
                                        @Nullable String contextId,
                                        @NotNull Project project,
                                        boolean physical) {
    final String strContext = getContext(text, language, contextId);

    final String patternInContext = (context == PatternTreeContext.File) ? text : strContext.replace(PATTERN_PLACEHOLDER, text);

    @NonNls final String name = "__dummy." + fileType.getDefaultExtension();
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(name, language, patternInContext, physical, true);
    if (file == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    if (context == PatternTreeContext.File) {
      return new PsiElement[] {file};
    }

    final int offset = strContext.indexOf(PATTERN_PLACEHOLDER);
    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    PsiElement topElement = element;
    element = element.getParent();

    final int patternLength = text.length();
    while (element != null) {
      if (element.getTextRange().getStartOffset() == offset && element.getTextLength() <= patternLength) {
        topElement = element;
      }
      element = element.getParent();
    }

    if (topElement instanceof PsiFile) {
      return topElement.getChildren();
    }

    final List<PsiElement> result = new SmartList<>();
    result.add(topElement);
    topElement = topElement.getNextSibling();

    final int endOffset = offset + patternLength;
    while (topElement != null && topElement.getTextRange().getEndOffset() <= endOffset) {
      result.add(topElement);
      topElement = topElement.getNextSibling();
    }

    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  @NotNull
  public List<PatternContext> getPatternContexts() {
    return Collections.emptyList();
  }

  @NotNull
  protected String getContext(@NotNull String pattern, @Nullable Language language, @Nullable String contextId) {
    return PATTERN_PLACEHOLDER;
  }

  @Nullable
  public PsiCodeFragment createCodeFragment(Project project, String text, String contextId) {
    return null;
  }

  /**
   * This method is called while holding a read action.
   */
  public String getCodeFragmentText(PsiFile fragment) {
    return fragment.getText();
  }

  @NotNull
  public abstract Class<? extends TemplateContextType> getTemplateContextTypeClass();

  @Nullable
  public LanguageFileType detectFileType(@NotNull PsiElement context) {
    return null;
  }

  @Nullable
  public StructuralReplaceHandler getReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
    return null;
  }

  public void checkSearchPattern(CompiledPattern pattern) {}

  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    final String fileType = StringUtil.toLowerCase(options.getMatchOptions().getFileType().getName());
    throw new UnsupportedPatternException(SSRBundle.message("replacement.not.supported.for.filetype", fileType));
  }

  public boolean shouldShowProblem(PsiErrorElement error) {
    return false;
  }

  // only for nodes not filtered by lexical-nodes filter; they can be by default
  public boolean canBeVarDelimiter(@NotNull PsiElement element) {
    return false;
  }

  public String getText(PsiElement match, int start, int end) {
    final String matchText = match.getText();
    if (start == 0 && end == -1) return matchText;
    return matchText.substring(start, end == -1 ? matchText.length() : end);
  }

  @NotNull
  public String getTypedVarString(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      if (name != null) {
        return name;
      }
    }
    return element.getText();
  }

  public String getMeaningfulText(PsiElement element) {
    return getTypedVarString(element);
  }

  public String getAlternativeText(PsiElement element, String previousText) {
    return null;
  }

  public PsiElement updateCurrentNode(PsiElement node) {
    return node;
  }

  public PsiElement extendMatchedByDownUp(PsiElement node) {
    return node;
  }

  public PsiElement extendMatchOnePsiFile(PsiElement file) {
    return file;
  }

  public LanguageFileType getDefaultFileType(@Nullable LanguageFileType fileType) {
    return fileType;
  }

  public Configuration[] getPredefinedTemplates() {
    return Configuration.EMPTY_ARRAY;
  }

  public void provideAdditionalReplaceOptions(@NotNull PsiElement node, ReplaceOptions options, ReplacementBuilder builder) {}

  public void handleSubstitution(ParameterInfo info, MatchResult match, StringBuilder result, ReplacementInfo replacementInfo) {
    if (info.getName().equals(match.getName())) {
      final String replacementString;
      boolean removeSemicolon = false;
      if (match.hasChildren() && !match.isScopeMatch()) {
        // compound matches
        final StringBuilder buf = new StringBuilder();

        for (final MatchResult matchResult : match.getChildren()) {
          final PsiElement currentElement = matchResult.getMatch();

          if (buf.length() > 0) {
            if (info.isArgumentContext()) {
              buf.append(',');
            }
            else {
              final PsiElement sibling = currentElement.getPrevSibling();
              buf.append(sibling instanceof PsiWhiteSpace ? sibling.getText() : " ");
            }
          }

          buf.append(matchResult.getMatchImage());
          removeSemicolon = currentElement instanceof PsiComment;
        }
        replacementString = buf.toString();
      }
      else {
        if (info.isStatementContext()) {
          removeSemicolon = match.getMatch() instanceof PsiComment;
        }
        replacementString = match.getMatchImage();
      }

      int offset = Replacer.insertSubstitution(result, 0, info, replacementString);
      if (info.isStatementContext() &&
          (removeSemicolon || StringUtil.endsWithChar(replacementString, ';') || StringUtil.endsWithChar(replacementString, '}'))) {
        final int start = info.getStartIndex() + offset;
        result.delete(start, start + 1);
      }
    }
  }

  public void handleNoSubstitution(ParameterInfo info, StringBuilder result) {
    if (info.isHasCommaBefore()) {
      result.delete(info.getBeforeDelimiterPos(), info.getBeforeDelimiterPos() + 1);
    }
    else if (info.isHasCommaAfter()) {
      result.delete(info.getAfterDelimiterPos(), info.getAfterDelimiterPos() + 1);
    }
  }

  @Contract("null -> false")
  public boolean isIdentifier(@Nullable PsiElement element) {
    return false;
  }

  @NotNull
  public Collection<String> getReservedWords() {
    return Collections.emptySet();
  }

  public boolean isDocCommentOwner(PsiElement match) {
    return false;
  }

  @Contract("!null -> !null")
  public PsiElement getPresentableElement(PsiElement element) {
    return isIdentifier(element) ? element.getParent() : element;
  }

  /**
   * Override this method to influence which UI controls are shown when editing the constraints of the specified variable.
   *
   * @param constraintName  the name of the constraint controls for which applicability is considered.
   *                        See {@link UIUtil} for predefined constraint names
   * @param variableNode    the psi element corresponding to the current variable
   * @param completePattern true, if the current variableNode encompasses the complete pattern. The variableNode can also be null in this case.
   * @param target          true, if the current variableNode is the target of the search
   * @return true, if the requested constraint is applicable and the corresponding UI should be shown when editing the variable; false otherwise
   */
  public boolean isApplicableConstraint(String constraintName, @Nullable PsiElement variableNode, boolean completePattern, boolean target) {
    switch (constraintName) {
      case UIUtil.MINIMUM_ZERO:
        if (target) return false;
      case UIUtil.MAXIMUM_UNLIMITED:
      case UIUtil.TEXT:
      case UIUtil.REFERENCE:
        return !completePattern;
    }
    return false;
  }

  public final boolean isApplicableConstraint(String constraintName,
                                              List<? extends PsiElement> nodes,
                                              boolean completePattern,
                                              boolean target) {
    if (nodes.isEmpty()) {
      return isApplicableConstraint(constraintName, (PsiElement)null, completePattern, target);
    }
    boolean result = true;
    for (PsiElement node : nodes) {
      result &= isApplicableConstraint(constraintName, node, completePattern, target);
    }
    return result;
  }
}
