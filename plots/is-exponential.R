# Rscript is-exponential.R data.csv
# Given a data set, compute the empirical CDF of the data set and then plot the
# original dataset on the x-axis and the Log of the CCDF on the y-axis. If the
# data set is exponential then the plot should be a straight line with slope -λ.
#
# This script can be used independently on a stand alone dataset with one
# variable.
require(ggplot2)
require(reshape2)

args <- commandArgs(TRUE)
data <- scan(args[1])
data <- sort(data)

# strip the outliers; builtin for doing this?
valid <- data[1:(length(data)*0.96)]

# construct the empirical CDF
ecdf <- (1:length(valid))/length(valid)
df <- data.frame(cbind(x=valid, y=ecdf))
# if this is exponential, get the slope and the x intercept
model <- lm(log(1-y)~x, data=df, subset=seq(1,length(valid)-1))
summary(model)

# plot the ccdf log(y) and the linear model. does it fit?
ggplot(aes(x=x, y=log(1-y)), data=df[1:length(valid)-1,]) +
    geom_line(color="blue") +
    geom_smooth(method="lm", color="red", size=0.3) +
    labs(x="Data", y="CCDF of Data Log(y)", title="Line of Best Fit on Log(CCDF) of Data")
ggsave("plots/is-exponential.png")
