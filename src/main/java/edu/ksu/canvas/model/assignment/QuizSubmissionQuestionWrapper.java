package edu.ksu.canvas.model.assignment;

import java.util.List;

/**
 * Wrapper class made necessary because canvas returns an object that
 * contains a list of QuizSubmissionQuestion Objects, with only one item in the list.
 * Rather than either just a list, or just the object....
 */
public class QuizSubmissionQuestionWrapper {

    private List<QuizSubmissionQuestion> quizSubmissionQuestions;

    public List<QuizSubmissionQuestion> getQuizSubmissionQuestions() {
        return quizSubmissionQuestions;
    }

    public void setQuizsubmissionquestions(List<QuizSubmissionQuestion> quizSubmissionQuestions) {
        this.quizSubmissionQuestions = quizSubmissionQuestions;
    }
}