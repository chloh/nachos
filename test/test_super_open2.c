#include "stdio.h"
#include "stdlib.h"

int main(int agrc, char *argv[]) {
  char* sort = "sort.o";
  char* sh = "sh.o";
  char* mv = "mv.o";
  char* halt = "halt.o";
  char* cp = "cp.o";

  char* name = "hello";
  int fd1 = open(name);
  int fd2 = open(sort);
  int fd3 = open(sh);
  int fd4 = open(mv);
  int fd5 = open(halt);
  int fd6 = open(cp);
  int fd7 = open(cp);
  int fd8 = open(cp);

  char* prgm = "test_super_open.coff";
  int num_args = 0;
  char** arg_val;
  int success = exec(prgm, num_args, arg_val);
  printf("success: %d\n", success);
  int fd9 = open(cp);
  int fd10 = open(cp);
  int fd11 = open(cp);
  int fd12 = open(cp);
  int fd13 = open(cp);
  int fd14 = open(cp);
  int fd15 = open(cp);
  int fd16 = open(cp);
  printf("fd1: %d\n", fd1);
  printf("fd2: %d\n", fd2);
  printf("fd3: %d\n", fd3);
  printf("fd4: %d\n", fd4);
  printf("fd5: %d\n", fd5);
  printf("fd6: %d\n", fd6);
  printf("fd7: %d\n", fd7);
  printf("fd8: %d\n", fd8);
  printf("fd9: %d\n", fd9);
  printf("fd10: %d\n", fd10);
  printf("fd11: %d\n", fd11);
  printf("fd12: %d\n", fd12);
  printf("fd13: %d\n", fd13);
  printf("fd14: %d\n", fd14);
  printf("fd15: %d\n", fd15);
  printf("fd16: %d\n", fd16);
  //char* text = "hello world";
  //write(fd, text, 5);
  //int success = close(fd);
  //printf("success: %d\n", success);
  return 0;
}
