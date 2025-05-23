package io.cucumber.gherkin;

import io.cucumber.messages.IdGenerator;
import io.cucumber.messages.types.DataTable;
import io.cucumber.messages.types.DocString;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleDocString;
import io.cucumber.messages.types.PickleStep;
import io.cucumber.messages.types.PickleStepArgument;
import io.cucumber.messages.types.PickleStepType;
import io.cucumber.messages.types.PickleTable;
import io.cucumber.messages.types.PickleTableCell;
import io.cucumber.messages.types.PickleTableRow;
import io.cucumber.messages.types.PickleTag;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.RuleChild;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.StepKeywordType;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import io.cucumber.messages.types.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

class PickleCompiler {

    private final IdGenerator idGenerator;

    PickleCompiler(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    List<Pickle> compile(GherkinDocument gherkinDocument, String uri) {
        List<Pickle> pickles = new ArrayList<>();
        if (!gherkinDocument.getFeature().isPresent()) {
            return pickles;
        }
        Feature feature = gherkinDocument.getFeature().get();

        String language = feature.getLanguage();

        compileFeature(pickles, feature, language, uri);
        return pickles;
    }

    private void compileFeature(List<Pickle> pickles, Feature feature, String language, String uri) {
        List<Tag> tags = feature.getTags();
        List<Step> featureBackgroundSteps = new ArrayList<>();
        for (FeatureChild featureChild : feature.getChildren()) {
            if (featureChild.getBackground().isPresent()) {
                featureBackgroundSteps.addAll(featureChild.getBackground().get().getSteps());
            } else if (featureChild.getRule().isPresent()) {
                compileRule(pickles, featureChild.getRule().get(), tags, featureBackgroundSteps, language, uri);
            } else if (featureChild.getScenario().isPresent()) {
                Scenario scenario = featureChild.getScenario().get();
                if (scenario.getExamples().isEmpty()) {
                    compileScenario(pickles, scenario, tags, featureBackgroundSteps, language, uri);
                } else {
                    compileScenarioOutline(pickles, scenario, tags, featureBackgroundSteps, language, uri);
                }
            }
        }
    }

    private void compileRule(List<Pickle> pickles, Rule rule, List<Tag> parentTags, List<Step> featureBackgroundSteps, String language, String uri) {
        List<Step> ruleBackgroundSteps = new ArrayList<>(featureBackgroundSteps);

        List<Tag> ruleTags = new ArrayList<>();
        ruleTags.addAll(parentTags);
        ruleTags.addAll(rule.getTags());

        for (RuleChild ruleChild : rule.getChildren()) {
            if (ruleChild.getBackground().isPresent()) {
                ruleBackgroundSteps.addAll(ruleChild.getBackground().get().getSteps());
            } else if (ruleChild.getScenario().isPresent()) {
                Scenario scenario = ruleChild.getScenario().get();
                if (scenario.getExamples().isEmpty()) {
                    compileScenario(pickles, scenario, ruleTags, ruleBackgroundSteps, language, uri);
                } else {
                    compileScenarioOutline(pickles, scenario, ruleTags, ruleBackgroundSteps, language, uri);
                }
            }
        }
    }

    private void compileScenario(List<Pickle> pickles, Scenario scenario, List<Tag> parentTags, List<Step> backgroundSteps, String language, String uri) {
        List<PickleStep> steps = new ArrayList<>();
        if (!scenario.getSteps().isEmpty()) {
            List<Step> allSteps = new ArrayList<>();
            allSteps.addAll(backgroundSteps);
            allSteps.addAll(scenario.getSteps());

            StepKeywordType lastKeywordType = StepKeywordType.UNKNOWN;
            for (Step step : allSteps) {
                StepKeywordType stepKeywordType = step.getKeywordType().get();
                if (stepKeywordType != StepKeywordType.CONJUNCTION)
                    lastKeywordType = stepKeywordType;

                steps.add(pickleStep(step, lastKeywordType));
            }
        }

        List<Tag> scenarioTags = new ArrayList<>();
        scenarioTags.addAll(parentTags);
        scenarioTags.addAll(scenario.getTags());

        List<String> sourceIds = singletonList(scenario.getId());

        Pickle pickle = new Pickle(
                idGenerator.newId(),
                uri,
                scenario.getName(),
                language,
                steps,
                pickleTags(scenarioTags),
                sourceIds
        );
        pickles.add(pickle);
    }

    private void compileScenarioOutline(List<Pickle> pickles, Scenario scenario, List<Tag> featureTags, List<Step> backgroundSteps, String language, String uri) {
        for (final Examples examples : scenario.getExamples()) {
            if (!examples.getTableHeader().isPresent()) continue;
            List<TableCell> variableCells = examples.getTableHeader().get().getCells();
            for (final TableRow valuesRow : examples.getTableBody()) {
                List<TableCell> valueCells = valuesRow.getCells();

                List<PickleStep> steps = new ArrayList<>();
                StepKeywordType lastKeywordType = StepKeywordType.UNKNOWN;

                if (!scenario.getSteps().isEmpty())
                    for (Step step : backgroundSteps) {
                        StepKeywordType stepKeywordType = step.getKeywordType().get();
                        if (stepKeywordType != StepKeywordType.CONJUNCTION)
                            lastKeywordType = stepKeywordType;

                        steps.add(pickleStep(step, lastKeywordType));
                    }


                List<Tag> tags = new ArrayList<>();
                tags.addAll(featureTags);
                tags.addAll(scenario.getTags());
                tags.addAll(examples.getTags());

                for (Step scenarioOutlineStep : scenario.getSteps()) {
                    StepKeywordType stepKeywordType = scenarioOutlineStep.getKeywordType().get();
                    if (stepKeywordType != StepKeywordType.CONJUNCTION)
                        lastKeywordType = stepKeywordType;

                    PickleStep pickleStep = pickleStep(scenarioOutlineStep, variableCells, valuesRow, lastKeywordType);

                    steps.add(pickleStep);
                }

                List<String> sourceIds = asList(scenario.getId(), valuesRow.getId());
                Pickle pickle = new Pickle(
                        idGenerator.newId(),
                        uri,
                        interpolate(scenario.getName(), variableCells, valueCells),
                        language,
                        steps,
                        pickleTags(tags),
                        sourceIds
                );

                pickles.add(pickle);
            }
        }
    }

    private PickleTable pickleDataTable(DataTable dataTable, List<TableCell> variableCells, List<TableCell> valueCells) {
        List<TableRow> rows = dataTable.getRows();
        List<PickleTableRow> newRows = new ArrayList<>(rows.size());
        for (TableRow row : rows) {
            List<TableCell> cells = row.getCells();
            List<PickleTableCell> newCells = new ArrayList<>(cells.size());
            for (TableCell cell : cells) {
                newCells.add(new PickleTableCell(interpolate(cell.getValue(), variableCells, valueCells)));
            }
            newRows.add(new PickleTableRow(newCells));
        }
        return new PickleTable(newRows);
    }

    private PickleDocString pickleDocString(DocString docString, List<TableCell> variableCells, List<TableCell> valueCells) {
        return new PickleDocString(
                docString.getMediaType().isPresent() ? interpolate(docString.getMediaType().get(), variableCells, valueCells) : null,
                interpolate(docString.getContent(), variableCells, valueCells)
        );
    }


    private static final Map<StepKeywordType, PickleStepType> pickleStepTypeFromKeywordType = new EnumMap<>(StepKeywordType.class);

    static {
        pickleStepTypeFromKeywordType.put(StepKeywordType.UNKNOWN, PickleStepType.UNKNOWN);
        pickleStepTypeFromKeywordType.put(StepKeywordType.CONTEXT, PickleStepType.CONTEXT);
        pickleStepTypeFromKeywordType.put(StepKeywordType.ACTION, PickleStepType.ACTION);
        pickleStepTypeFromKeywordType.put(StepKeywordType.OUTCOME, PickleStepType.OUTCOME);
    }

    private PickleStep pickleStep(Step step, List<TableCell> variableCells, TableRow valuesRow, StepKeywordType keywordType) {
        List<TableCell> valueCells = valuesRow == null ? emptyList() : valuesRow.getCells();
        String stepText = interpolate(step.getText(), variableCells, valueCells);

        PickleStepArgument argument = null;
        if (step.getDataTable().isPresent()) {
            argument = new PickleStepArgument(null, pickleDataTable(step.getDataTable().get(), variableCells, valueCells));
        }
        if (step.getDocString().isPresent()) {
            argument = new PickleStepArgument(pickleDocString(step.getDocString().get(), variableCells, valueCells), null);
        }


        List<String> astNodeIds;
        if (valuesRow != null) {
            astNodeIds = Arrays.asList(step.getId(), valuesRow.getId());

        } else {
            astNodeIds = singletonList(step.getId());
        }

        return new PickleStep(
                argument,
                astNodeIds,
                idGenerator.newId(),
                pickleStepTypeFromKeywordType.get(keywordType),
                stepText
        );
    }

    private PickleStep pickleStep(Step step, StepKeywordType keywordType) {
        return pickleStep(step, emptyList(), null, keywordType);
    }

    private String interpolate(String name, List<TableCell> variableCells, List<TableCell> valueCells) {
        int col = 0;
        for (TableCell variableCell : variableCells) {
            TableCell valueCell = valueCells.get(col++);
            String header = variableCell.getValue();
            String value = valueCell.getValue();
            name = name.replace("<" + header + ">", value);
        }
        return name;
    }

    private List<PickleTag> pickleTags(List<Tag> tags) {
        if (tags.isEmpty()) {
            return emptyList();
        }
        List<PickleTag> result = new ArrayList<>();
        for (Tag tag : tags) {
            result.add(pickleTag(tag));
        }
        return result;
    }

    private PickleTag pickleTag(Tag tag) {
        return new PickleTag(tag.getName(), tag.getId());
    }

}
