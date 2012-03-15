#include "stdio.h"
#include "stdlib.h"

int main(int argc, char *argv[]) {

  char* name = "test_creat.coff";
  int arg_count = 0;
  char* argv2[1];
  exec(name, argc, argv);
  printf("after exec");


  return 0;
}
