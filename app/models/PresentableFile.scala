package models

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

case class PresentableFile(oid:String, attributes: FileAttributes, gnmMetadata: Option[GnmMetadata], customMeta:Option[String])

object PresentableFile extends ((String, FileAttributes,Option[GnmMetadata], Option[String])=>PresentableFile) {
  def fromObjectMatrixEntry(src:ObjectMatrixEntry):Option[PresentableFile] =
    src.fileAttribues.map(attribs=>PresentableFile(src.oid, attribs, GnmMetadata.fromObjectMatrixEntry(src) ,src.attributes.map(_.dumpString(None))))

}

case class GnmMetadata(`type`: String,projectId:Option[String],commissionId:Option[String],
                       projectName:Option[String], commissionName:Option[String],workingGroupName:Option[String],
                       masterId:Option[String], masterName:Option[String], masterUser:Option[String],
                       deliverableBundleId:Option[Int], deliverableVersion:Option[Int],deliverableType:Option[String]
                      )

object GnmMetadata {
  private val logger = LoggerFactory.getLogger(getClass)
  def fromObjectMatrixEntry(src:ObjectMatrixEntry):Option[GnmMetadata] = Try {
    src.attributes.map(attribs => GnmMetadata(
      attribs.stringValues("GNM_TYPE"),
      attribs.stringValues.get("GNM_PROJECT_ID"),
      attribs.stringValues.get("GNM_COMMISSION_ID"),
      attribs.stringValues.get("GNM_PROJECT_NAME"),
      attribs.stringValues.get("GNM_COMMISSION_NAME"),
      attribs.stringValues.get("GNM_WORKING_GROUP_NAME"),
      attribs.stringValues.get("GNM_MASTER_ID"),
      attribs.stringValues.get("GNM_MASTER_NAME"),
      attribs.stringValues.get("GNM_MASTER_USER"),
      attribs.intValues.get("GNM_DELIVERABLE_BUNDLE_ID"),
      attribs.intValues.get("GNM_DELIVERABLE_VERSION"),
      attribs.stringValues.get("GNM_DELIVERABLE_TYPE")
    ))
  } match {
    case Success(meta)=>meta
    case Failure(err)=>
      logger.warn(s"Could not get GNM custom metadata values for ${src.oid}: ", err)
      None
  }
}