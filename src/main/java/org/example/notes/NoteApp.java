package org.example.notes;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import javax.persistence.Persistence;
import javax.persistence.*;
import java.util.List;
import java.util.Optional;

public class NoteApp extends Application {

    private TextField titleField;
    private TextArea contentArea;
    private TextField tagField;
    private ListView<Note> notesList;

    private EntityManagerFactory entityManagerFactory;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        initializeDatabase();

        primaryStage.setTitle("Note App");

        titleField = new TextField();
        titleField.setPromptText("Title");

        contentArea = new TextArea();
        contentArea.setPromptText("Content");

        tagField = new TextField();
        tagField.setPromptText("Tag");

        Button addButton = new Button("Add");
        addButton.setOnAction(e -> addNote());

        Button updateButton = new Button("Update");
        updateButton.setOnAction(e -> updateNote());

        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> deleteNote());

        Button searchButton = new Button("Search");
        searchButton.setOnAction(e -> searchNotesDialog());

        notesList = new ListView<>();

        loadNotesFromDatabase();

        notesList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> displaySelectedNoteDetails(newValue));

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10, 10, 10, 10));
        layout.getChildren().addAll(
                titleField,
                contentArea,
                tagField,
                new HBox(10, addButton, updateButton, deleteButton, searchButton),
                notesList
        );

        Scene scene = new Scene(layout, 400, 400);
        primaryStage.setScene(scene);

        primaryStage.show();
    }

    private void initializeDatabase() {
        entityManagerFactory = Persistence.createEntityManagerFactory("NoteAppPU");
    }

    private void closeDatabase() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    private void loadNotesFromDatabase() {
        EntityManager entityManager = null;
        try {
            entityManager = entityManagerFactory.createEntityManager();
            entityManager.getTransaction().begin();

            List<Note> notes = entityManager.createQuery("SELECT n FROM Note n", Note.class).getResultList();

            entityManager.getTransaction().commit();
            notesList.getItems().addAll(notes);
        } finally {
            if (entityManager != null && entityManager.isOpen()) {
                entityManager.close();
            }
        }
    }


    private void addNote() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();

            String title = titleField.getText();
            String content = contentArea.getText();

            if (title.isEmpty() || content.isEmpty()) {
                showAlert("Title and content cannot be empty.", Alert.AlertType.ERROR);
                return;
            }

            Note newNote = new Note();
            newNote.setTitle(title);
            newNote.setContent(content);

            String tagTitle = tagField.getText();
            Tag existingTag = findExistingTag(entityManager, tagTitle);

            if (existingTag != null) {
                newNote.getTags().add(existingTag);
            } else if (!tagTitle.isEmpty()) {
                Tag newTag = new Tag();
                newTag.setTitle(tagTitle);
                newTag.setNote(newNote);
                newNote.getTags().add(newTag);
            }

            entityManager.persist(newNote);

            entityManager.getTransaction().commit();
            notesList.getItems().add(newNote);
            clearFields();
            showAlert("Note added successfully.", Alert.AlertType.INFORMATION);
        } catch (Exception e) {
            handleException(e);
        } finally {
            if (entityManager != null && entityManager.isOpen()) {
                entityManager.close();
            }
        }
    }

    private Tag findExistingTag(EntityManager entityManager, String tagTitle) {
        try {
            return entityManager.createQuery("SELECT t FROM Tag t WHERE t.title = :tagTitle", Tag.class)
                    .setParameter("tagTitle", tagTitle)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private void updateNote() {
        Note selectedNote = notesList.getSelectionModel().getSelectedItem();
        if (selectedNote != null) {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                entityManager.getTransaction().begin();

                selectedNote.setTitle(titleField.getText());
                selectedNote.setContent(contentArea.getText());

                entityManager.getTransaction().commit();
                notesList.refresh();
                clearFields();
                showAlert("Note updated successfully.", Alert.AlertType.INFORMATION);
            } catch (Exception e) {
                handleException(e);
            } finally {
                if (entityManager != null && entityManager.isOpen()) {
                    entityManager.close();
                }
            }
        }
    }

    private void deleteNote() {
        Note selectedNote = notesList.getSelectionModel().getSelectedItem();
        if (selectedNote != null) {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                entityManager.getTransaction().begin();

                entityManager.remove(entityManager.merge(selectedNote));

                entityManager.getTransaction().commit();
                notesList.getItems().remove(selectedNote);
                clearFields();
                showAlert("Note deleted successfully.", Alert.AlertType.INFORMATION);
            } catch (Exception e) {
                handleException(e);
            } finally {
                if (entityManager != null && entityManager.isOpen()) {
                    entityManager.close();
                }
            }
        }
    }

    private void searchNotes(String searchTerm) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();

            // Töm listan innan nya sökresultat läggs till
            notesList.getItems().clear();

            List<Note> searchResults = entityManager
                    .createQuery("SELECT n FROM Note n WHERE n.title LIKE :searchTerm OR :searchTerm IN elements(n.tags)", Note.class)
                    .setParameter("searchTerm", "%" + searchTerm + "%")
                    .getResultList();

            entityManager.getTransaction().commit();

            // nya sökresultat i listan
            notesList.getItems().addAll(searchResults);
        } catch (Exception e) {
            handleException(e);
        } finally {
            if (entityManager != null && entityManager.isOpen()) {
                entityManager.close();
            }
        }
    }


    private void searchNotesDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Search Notes");
        dialog.setHeaderText("Enter search term:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(this::searchNotes);
    }

    private void displaySelectedNoteDetails(Note note) {
        if (note != null) {
            titleField.setText(note.getTitle());
            contentArea.setText(note.getContent());
        } else {
            clearFields();
        }
    }

    private void clearFields() {
        titleField.clear();
        contentArea.clear();
        tagField.clear();
    }

    private void showAlert(String message, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void handleException(Exception e) {
        if (e instanceof PersistenceException && e.getCause() instanceof RollbackException) {
            showAlert("Error: " + e.getCause().getMessage(), Alert.AlertType.ERROR);
        } else {
            showAlert("Unexpected error occurred.", Alert.AlertType.ERROR);
        }
    }

    @Override
    public void stop() {
        closeDatabase();
    }
}
