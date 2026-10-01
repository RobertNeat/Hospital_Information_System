package robert_neat.his_backend.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Katalog badan obrazowych; klucz = kod badania. Do ponownego uzycia przez zlecenia obrazowe. */
public interface ImagingExamRepository extends JpaRepository<ImagingExam, String> {

    List<ImagingExam> findByModality(ImagingModality modality);
}
