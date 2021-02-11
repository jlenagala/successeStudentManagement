package lk.succes.student_management.asset.time_table.controller;


import lk.succes.student_management.asset.batch.entity.Batch;
import lk.succes.student_management.asset.batch.entity.enums.ClassDay;
import lk.succes.student_management.asset.batch.service.BatchService;
import lk.succes.student_management.asset.batch_student.service.BatchStudentService;
import lk.succes.student_management.asset.common_asset.model.DateTimeTable;
import lk.succes.student_management.asset.common_asset.model.enums.LiveDead;
import lk.succes.student_management.asset.hall.service.HallService;
import lk.succes.student_management.asset.subject.service.SubjectService;
import lk.succes.student_management.asset.teacher.service.TeacherService;
import lk.succes.student_management.asset.time_table.entity.TimeTable;
import lk.succes.student_management.asset.time_table.service.TimeTableService;
import lk.succes.student_management.util.service.DateTimeAgeService;
import lk.succes.student_management.util.service.MakeAutoGenerateNumberService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping( "/timeTable" )
public class TimeTableController {
  private final TimeTableService timeTableService;
  private final HallService hallService;
  private final SubjectService subjectService;
  private final TeacherService teacherService;
  private final BatchService batchService;
  private final BatchStudentService batchStudentService;
  private final MakeAutoGenerateNumberService makeAutoGenerateNumberService;
  private final DateTimeAgeService dateTimeAgeService;

  public TimeTableController(TimeTableService timeTableService, HallService hallService,
                             SubjectService subjectService, TeacherService teacherService, BatchService batchService,
                             BatchStudentService batchStudentService,
                             MakeAutoGenerateNumberService makeAutoGenerateNumberService,
                             DateTimeAgeService dateTimeAgeService) {
    this.timeTableService = timeTableService;
    this.hallService = hallService;
    this.subjectService = subjectService;
    this.teacherService = teacherService;
    this.batchService = batchService;
    this.batchStudentService = batchStudentService;
    this.makeAutoGenerateNumberService = makeAutoGenerateNumberService;
    this.dateTimeAgeService = dateTimeAgeService;
  }

  @GetMapping
  public String findAll(Model model) {
    model.addAttribute("timeTables",
                       timeTableService.findAll());
    return "timeTable/timeTable";
  }

  @GetMapping( "/byDate" )
  public String byDate(Model model) {
    List< DateTimeTable > dateTimeTables = new ArrayList<>();
    Map< LocalDateTime, List< TimeTable > > timeTableMap =
        timeTableService.findAll().stream().collect(Collectors.groupingBy(TimeTable::getStartAt));

    timeTableMap.forEach((x, y) -> {
      DateTimeTable timeTable = new DateTimeTable();
      timeTable.setDate(x.toLocalDate());
      timeTable.setTimeTables(y);
      dateTimeTables.add(timeTable);
    });
    model.addAttribute("timeTableMaps", dateTimeTables);
    return "timeTable/timeTableView";
  }


  @GetMapping( "/add" )
  public String form() {
    return "timeTable/dateChooser";
  }


  @PostMapping( "/add" )
  public String form(@RequestParam( "date" ) @DateTimeFormat( pattern = "yyyy-MM-dd" ) LocalDate date, Model model) {
    LocalDate today = LocalDate.now();
    //month
    Month month = today.getMonth();
//Day of week
    String dayOfWeek = date.getDayOfWeek().toString();

    return commonThing(model, date, true);
  }

  @GetMapping( "/view/{id}" )
  public String findById(@PathVariable Integer id, Model model) {
    model.addAttribute("timeTableDetail", timeTableService.findById(id));
    return "timeTable/timeTable-detail";
  }

  @GetMapping( "/edit/{date}" )
  public String editGet(@PathVariable( "date" ) @DateTimeFormat( pattern = "yyyy-MM-dd" ) LocalDate date, Model model) {
    return commonThing(model, date, false);
  }

  @PostMapping( "/edit" )
  public String editPost(@RequestParam( "date" ) @DateTimeFormat( pattern = "yyyy-MM-dd" ) LocalDate date,
                         Model model) {
    return commonThing(model, date, false);
  }

  @PostMapping( "/save" )
  public String persist(@Valid @ModelAttribute Batch batch, BindingResult bindingResult, Model model) {
    if ( bindingResult.hasErrors() ) {
      System.out.println(bindingResult.toString());
      return commonThing(model, batch.getDate(), true);
    }

    for ( TimeTable timeTable : batch.getTimeTables() ) {
      if ( timeTable.getId() == null ) {
        TimeTable lastTimeTable = timeTableService.lastTimeTable();
        if ( lastTimeTable == null ) {
          timeTable.setCode("SSTM" + makeAutoGenerateNumberService.numberAutoGen(null).toString());
        } else {
          timeTable.setCode("SSTM" + makeAutoGenerateNumberService.numberAutoGen(lastTimeTable.getCode().substring(4)).toString());
        }
      }
//todo need to send email to student who register in relevant batch
      timeTableService.persist(timeTable);
    }

    return "redirect:/timeTable";

  }

  private String commonThing(Model model, LocalDate date, boolean addStatus) {
    model.addAttribute("halls",
                       hallService.findAll().stream().filter(x -> x.getLiveDead().equals(LiveDead.ACTIVE)).collect(Collectors.toList()));

    LocalDateTime from = dateTimeAgeService.dateTimeToLocalDateStartInDay(date);
    LocalDateTime to = dateTimeAgeService.dateTimeToLocalDateEndInDay(date);


    List< Batch > batches = new ArrayList<>();

    Batch batchSend = new Batch();
    if ( addStatus ) {
      String dayOfWeek = date.getDayOfWeek().toString();
      //Day of week
      for ( Batch batch : batchService.findByClassDay(ClassDay.valueOf(dayOfWeek))
          .stream()//filter by using batch and in timeTable
          .filter(x -> x.getLiveDead().equals(LiveDead.ACTIVE) && timeTableService.availableTimeTableCheck(from,to,x))
          .collect(Collectors.toList()) ) {
        batch.setCount(batchStudentService.countByBatch(batch));
        batches.add(batch);
      }
      List< TimeTable > timeTables = new ArrayList<>();
      for ( Batch batch1 : batches ) {
        TimeTable timeTable = new TimeTable();
        timeTable.setBatch(batch1);
        timeTables.add(timeTable);
      }
      System.out.println("time table size " + timeTables.size());
      batchSend.setTimeTables(timeTables);
    } else {
      System.out.println("add status false");
      List<TimeTable> timeTables = timeTableService.findByCreatedAtIsBetween(from, to);
      System.out.println("date "+date+"  form "+from+"size "+ timeTables.size());
      batchSend.setTimeTables(timeTables);
    }


    model.addAttribute("batches", batchSend);
//    model.addAttribute("batches", batchService.findAll());

    model.addAttribute("addStatus", addStatus);
    model.addAttribute("date", date);
    model.addAttribute("liveDeads", LiveDead.values());
    return "timeTable/addTimeTable";
  }


}

