package com.cookieukw.SimTale.engine;

import java.util.*;

public class MagicEngine {
    private static final double LOG2 = Math.log(2);

    private final Map<String, Map<String, Double>> invertedIndex;
    private final Map<String, Double> scores;
    private final Set<String> askedQuestions;
    private final List<Animal> animals;
    private final List<Question> questions;

    private static final String[][] CORRELATED_GROUPS = {
        {"q1", "q2", "q3", "q4"},
        {"q13", "q14"},
        {"q5", "q10"}
    };

    public MagicEngine(List<Animal> animals, List<Question> questions) {
        this.animals = new ArrayList<>(animals);
        this.questions = new ArrayList<>(questions);
        this.askedQuestions = new LinkedHashSet<>();
        this.scores = new HashMap<>();
        this.invertedIndex = new HashMap<>();

        for (Animal animal : animals) {
            this.scores.put(animal.getId(), 0.0);
            for (Map.Entry<String, Double> entry : animal.getAnswers().entrySet()) {
                String qId = entry.getKey();
                Double weight = entry.getValue();
                this.invertedIndex.computeIfAbsent(qId, _ -> new HashMap<>()).put(animal.getId(), weight);
            }
        }
    }

    public void answerQuestion(String questionId, double value) {
        if (!this.askedQuestions.add(questionId)) return;

        Map<String, Double> questionData = this.invertedIndex.get(questionId);
        if (questionData == null) return;

        double strength = Math.abs(value);
        double multiplier = strength >= 1 ? 3 : strength >= 0.5 ? 1.5 : 0;

        for (Map.Entry<String, Double> entry : questionData.entrySet()) {
            String animalId = entry.getKey();
            Double weight = entry.getValue();
            
            double newScore = this.scores.getOrDefault(animalId, 0.0) + (value * weight * multiplier);
            this.scores.put(animalId, newScore);
        }
    }

    public String getBestQuestion() {
        List<Animal> sortedAnimals = getSortedAnimals();
        
        Animal medianAnimal = sortedAnimals.isEmpty() ? null : sortedAnimals.get(sortedAnimals.size() / 2);
        double medianVal = medianAnimal != null ? this.scores.getOrDefault(medianAnimal.getId(), 0.0) : 0.0;
        
        List<Animal> candidates = sortedAnimals.stream()
            .filter(a -> this.scores.getOrDefault(a.getId(), 0.0) >= medianVal)
            .limit(10)
            .toList();
            
        if (candidates.isEmpty()) return null;

        Set<String> deprioritized = getDeprioritizedQuestions();

        class ScoredQuestion {
            final String id;
            double entropy;
            final boolean isDeprioritized;

            ScoredQuestion(String id, double entropy, boolean isDeprioritized) {
                this.id = id;
                this.entropy = entropy;
                this.isDeprioritized = isDeprioritized;
            }
        }

        List<ScoredQuestion> scoredQuestions = new ArrayList<>();

        for (Question q : this.questions) {
            if (this.askedQuestions.contains(q.getId())) continue;

            double yesCount = 0;
            double noCount = 0;
            double totalWeight = 0;

            for (Animal animal : candidates) {
                Map<String, Double> animalWeights = this.invertedIndex.get(q.getId());
                double weight = (animalWeights != null) ? animalWeights.getOrDefault(animal.getId(), 0.0) : 0.0;
                
                if (weight > 0) {
                    yesCount += weight;
                } else if (weight < 0) {
                    noCount += Math.abs(weight);
                }
                totalWeight += Math.abs(weight);
            }

            if (totalWeight == 0) continue;

            double total = yesCount + noCount;
            double pYes = total > 0 ? yesCount / total : 0;
            double pNo = total > 0 ? noCount / total : 0;

            double entropy = 0;
            if (pYes > 0 && pYes < 1) {
                entropy = -(pYes * (Math.log(pYes) / LOG2) + pNo * (Math.log(pNo) / LOG2));
            }

            scoredQuestions.add(new ScoredQuestion(q.getId(), entropy, deprioritized.contains(q.getId())));
        }

        if (scoredQuestions.isEmpty()) return null;

        for (ScoredQuestion sq : scoredQuestions) {
            if (sq.isDeprioritized) {
                sq.entropy *= 0.4;
            }
        }

        scoredQuestions.sort((a, b) -> Double.compare(b.entropy, a.entropy));

        double bestEntropy = scoredQuestions.getFirst().entropy;
        double threshold = bestEntropy * 0.85;
        
        List<ScoredQuestion> topTier = scoredQuestions.stream()
            .filter(sq -> sq.entropy >= threshold)
            .limit(3)
            .toList();
            
        int randomIndex = (int) (Math.random() * topTier.size());
        return topTier.get(randomIndex).id;
    }

    public Animal checkVictory() {
        List<Animal> sorted = getSortedAnimals();

        if (sorted.isEmpty()) return null;
        if (sorted.size() == 1) return sorted.get(0);

        double top1Score = this.scores.getOrDefault(sorted.get(0).getId(), 0.0);
        double top2Score = this.scores.getOrDefault(sorted.get(1).getId(), 0.0);

        if ((top1Score - top2Score >= 6 && this.askedQuestions.size() >= 3) ||
            this.askedQuestions.size() >= 15) {
            return sorted.getFirst();
        }

        return null;
    }

    private List<Animal> getSortedAnimals() {
        List<Animal> sorted = new ArrayList<>(this.animals);
        sorted.sort((a, b) -> {
            double scoreA = this.scores.getOrDefault(a.getId(), 0.0);
            double scoreB = this.scores.getOrDefault(b.getId(), 0.0);
            int scoreCompare = Double.compare(scoreB, scoreA);
            if (scoreCompare != 0) return scoreCompare;
            return Integer.compare(b.getPlayCount(), a.getPlayCount());
        });
        return sorted;
    }

    private Set<String> getDeprioritizedQuestions() {
        Set<String> deprioritized = new HashSet<>();

        for (String[] group : CORRELATED_GROUPS) {
            boolean anyAnswered = false;
            for (String qId : group) {
                if (this.askedQuestions.contains(qId)) {
                    anyAnswered = true;
                    break;
                }
            }
            
            if (anyAnswered) {
                for (String qId : group) {
                    if (!this.askedQuestions.contains(qId)) {
                        deprioritized.add(qId);
                    }
                }
            }
        }

        return deprioritized;
    }

    public Map<String, Double> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    public Set<String> getAskedQuestions() {
        return Collections.unmodifiableSet(askedQuestions);
    }

    public List<Animal> getAnimals() {
        return Collections.unmodifiableList(animals);
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
}
